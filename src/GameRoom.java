//> [ไฟล์นี้ทำอะไร] 'สมองของเกม' ฝั่ง Server มีห้องเดียว ใช้ร่วมกันทุก Client
//> Server เป็นผู้ถือ: เวลา คำใบ้ ช่องตัวอักษร คะแนน และเป็นผู้ตัดสินถูก/ผิดเพียงผู้เดียว แล้ว broadcast ให้ทุกเครื่องพร้อมกัน
//> มี Thread อยู่ 2 แบบที่เข้ามาใช้ object นี้พร้อมกัน:
//>   1) Thread ของ ClientHandler (1 ต่อ Client) เรียก join / leave / start / handleGuess
//>   2) Thread เกม (1 อัน) เรียก run() เพื่อเดินเกมทีละรอบ
//> เมธอดที่ synchronized = มีล็อกเดียวต่อห้อง เข้าได้ทีละ Thread เท่านั้น ข้อมูลในห้องจึงไม่พัง
import java.util.ArrayList;
import java.util.Random;

/**
 * ห้องเกม: ศูนย์กลางควบคุมเกมทั้งหมดฝั่ง Server (เวลา คำใบ้ ช่องตัวอักษร คะแนน)
 * แล้วส่งข้อมูลให้ทุก Client พร้อมกัน (broadcast)
 *
 * คลาสนี้ implements Runnable: เมธอด run() คือ "ลูปเกม" ที่รันใน Thread แยก
 * ส่วนเมธอดที่เป็น synchronized = ให้ทำงานได้ทีละ Thread เพราะหลาย Thread
 * (Client แต่ละคน + ลูปเกม) ใช้ข้อมูลในห้องเดียวกันพร้อมกัน
 *
 * ข้อความที่ส่งไป Client (รูปแบบ คำสั่ง#ข้อมูล):
 *   WELCOME#ชื่อ   STATE#LOBBY#ขั้นต่ำ#หัวหน้า   STATE#PLAYING
 *   SCORES#ชื่อ:คะแนน:ตอบแล้ว;...   ROUND#รอบ#ทั้งหมด#คำใบ้#วินาที#pattern
 *   HINT#pattern   TICK#วินาทีที่เหลือ   MSG#ข้อความ
 *   ROUNDEND#เฉลย   GAMEOVER#ผู้ชนะ   ERROR#ข้อความ
 */
//> implements Runnable: run() ด้านล่างคือ 'ลูปเกม' ที่ถูกรันใน Thread ใหม่ (ดู start())
public class GameRoom implements Runnable {
    //> TOTAL_ROUNDS = จำนวนรอบต่อเกม (static final = ค่าคงที่ของคลาส ใช้ GameRoom.TOTAL_ROUNDS ได้)
    public static final int TOTAL_ROUNDS = 10;
    //> ROUND_SECONDS = เวลาต่อรอบ (วินาที)
    public static final int ROUND_SECONDS = 30;

    //> minPlayers = ผู้เล่นขั้นต่ำที่ต้องมีถึงจะเริ่มเกมได้ (รับมาจาก GameServer)
    private int minPlayers;
    //> clients = ผู้เล่นที่อยู่ในห้องตอนนี้ ตำแหน่ง 0 = คนแรกที่เข้า = หัวหน้าห้อง
    private ArrayList<ClientHandler> clients = new ArrayList<>();

    //> playing = true ตั้งแต่เริ่มเกมจนกลับห้องรอ (ระหว่างนี้ไม่รับคนใหม่)
    private boolean playing = false;        // กำลังแข่งอยู่หรือไม่
    //> roundActive = true ระหว่างรอบกำลังเล่น (รับคำตอบได้) false ตอนหมดเวลา/พักเฉลย
    private boolean roundActive = false;    // รอบนี้ยังไม่จบ
    //> current = คำของรอบนี้ (คำตอบจริง)
    private WordEntry current;              // คำของรอบนี้
    //> slots = ช่องตัวอักษรของคำนี้
    private ArrayList<String> slots = new ArrayList<>();
    //> revealed[i] = true ถ้าช่องที่ i ถูกเปิดช่วยแล้ว (boolean[] = อาร์เรย์ ค่าเริ่มต้น false)
    private boolean[] revealed = new boolean[0];   // ช่องไหนเปิดแล้ว
    //> secondsLeft = วินาทีที่เหลือของรอบ (Server นับเอง)
    private int secondsLeft;
    //> firstSolver = true จนกว่าจะมีคนตอบถูกคนแรก (ใครตอบถูกก่อนได้โบนัส +10)
    private boolean firstSolver;

    //> Constructor: GameServer สร้างห้องโดยส่งจำนวนผู้เล่นขั้นต่ำมา
    public GameRoom(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    // ================= จัดการผู้เล่น =================

    //> join: ผู้เล่นขอเข้าห้อง คืน true = เข้าได้ / false = ปฏิเสธ
    //> (เรียกจาก ClientHandler เมื่อได้รับ JOIN#ชื่อ)
    public synchronized boolean join(ClientHandler h, String requestedName) {
        if (playing) {
            //> ปฏิเสธถ้าเกมกำลังเล่น: ส่ง ERROR ให้เฉพาะคนนี้ (h.send) ไม่ใช่ broadcast
            //> คืน false -> ClientHandler จะ break แล้วตัดการเชื่อมต่อ
            h.send("ERROR" + Protocol.SEP + "เกมกำลังเล่นอยู่ กรุณารอให้จบเกมก่อน");
            return false;
        }
        //> ปฏิเสธถ้าห้องเต็ม
        if (clients.size() >= Protocol.MAX_PLAYERS) {
            h.send("ERROR" + Protocol.SEP + "ห้องเต็มแล้ว");
            return false;
        }
        //> ล้างชื่อ (ตัดตัวอักษรต้องห้าม จำกัดความยาว)
        String name = cleanName(requestedName);
        //> baseName เก็บชื่อต้นฉบับไว้ตอนต่อเลข
        String baseName = name;
        int number = 2;
        //> ชื่อซ้ำกับคนที่อยู่แล้ว -> ต่อเลขท้าย สมชาย, สมชาย2, สมชาย3 ... จนกว่าจะไม่ซ้ำ
        while (nameTaken(name)) {           // ชื่อซ้ำ -> ต่อเลขท้ายชื่อ
            name = baseName + number;
            number++;
        }
        //> สร้าง Player ให้ ClientHandler คนนี้ (ClientHandler.getPlayer() จะเริ่มไม่เป็น null)
        h.setPlayer(new Player(name));
        //> เพิ่มเข้ารายชื่อ (ต่อท้ายลิสต์ ถ้าเป็นคนแรกจะอยู่ตำแหน่ง 0 = หัวหน้า)
        clients.add(h);
        //> ส่ง WELCOME#ชื่อ เฉพาะคนนี้ เพื่อบอกชื่อที่ได้จริง
        h.send("WELCOME" + Protocol.SEP + name);
        //> broadcast = ส่งให้ 'ทุกคน' (รวมคนที่เพิ่งเข้า) แจ้งว่ามีคนเข้าห้อง
        broadcast("MSG" + Protocol.SEP + name + " เข้าร่วมห้อง");
        //> ส่งสถานะห้องรอ (ชื่อหัวหน้า, ขั้นต่ำ) ให้ทุกคนอัปเดตหน้าจอ
        broadcastState();
        //> ส่งตารางคะแนนล่าสุด
        broadcastScores();
        return true;
    }

    //> cleanName: ตัดอักขระ # : ; , ออกจากชื่อ เพราะเป็นตัวคั่นในโปรโตคอล
    //> (# คั่นข้อความ, : และ ; คั่นใน SCORES, ',' คั่นใน pattern) ถ้าไม่ตัดจะทำให้ Client แยกข้อความผิดและเกมพัง
    /** ตัดตัวอักษรที่ใช้เป็นตัวคั่นออกจากชื่อ และจำกัดความยาว */
    private String cleanName(String text) {
        //> result = ชื่อที่สะอาดแล้ว (สร้างทีละตัวอักษร)
        String result = "";
        //> วนทีละตัวอักษรของชื่อที่พิมพ์มา
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            //> เอาไว้เฉพาะตัวที่ไม่ใช่ตัวคั่น
            if (c != '#' && c != ':' && c != ';' && c != ',') {
                result = result + c;
            }
        }
        //> trim() = ตัดช่องว่างหัวท้าย
        result = result.trim();
        //> ชื่อว่าง -> ใช้ชื่อเริ่มต้น "ผู้เล่น"
        if (result.equals("")) {
            result = "ผู้เล่น";
        }
        //> จำกัดไม่เกิน 14 ตัวอักษร (กันชื่อยาวล้นช่องคะแนน)
        if (result.length() > 14) {
            result = result.substring(0, 14);
        }
        return result;
    }

    //> nameTaken: เช็กว่าชื่อนี้มีคนใช้อยู่แล้วหรือยัง วนเทียบทุกคนในห้อง
    private boolean nameTaken(String name) {
        for (ClientHandler c : clients) {
            if (c.getPlayer().getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    //> leave: มีผู้เล่นออก (ปิดหน้าต่าง/สายหลุด/กดออก) ClientHandler เรียกเมื่อจบ Thread
    public synchronized void leave(ClientHandler h) {
        //> ถ้าไม่เคยอยู่ในห้อง (เช่นถูกปฏิเสธตอน join) ไม่ต้องทำอะไร
        if (!clients.contains(h)) {
            return;                         // ยังไม่เคยเข้าห้อง
        }
        //> เอาออกจากรายชื่อ (ถ้าเป็นหัวหน้า คนถัดไปจะขยับมาเป็นตำแหน่ง 0 = หัวหน้าคนใหม่อัตโนมัติ)
        clients.remove(h);
        //> แจ้งทุกคนที่เหลือ
        broadcast("MSG" + Protocol.SEP + h.getPlayer().getName() + " ออกจากเกม");
        //> ถ้าอยู่ห้องรอ ให้ส่งสถานะใหม่ เพื่อให้จอรู้ว่าใครเป็นหัวหน้า
        if (!playing) {
            broadcastState();
        }
        //> ส่งตารางคะแนนใหม่ (แถวของคนที่ออกหายไป)
        broadcastScores();
    }

    //> start: ขอเริ่มเกม (เรียกเมื่อได้รับ START)
    public synchronized void start(ClientHandler h) {
        //> กำลังเล่นอยู่แล้ว ไม่ทำอะไร (กันกดซ้ำ)
        if (playing) {
            return;
        }
        //> clients.get(0) != h  เทียบ 'ตัว object' (ไม่ใช่เทียบข้อความ): คนที่กดไม่ใช่หัวหน้า -> ปฏิเสธ
        if (clients.get(0) != h) {          // คนแรกที่เข้าห้อง = หัวหน้าห้อง
            h.send("MSG" + Protocol.SEP + "เฉพาะหัวหน้าห้องเท่านั้นที่เริ่มเกมได้");
            return;
        }
        //> ผู้เล่นไม่ถึงขั้นต่ำ (ค่าเริ่มต้น 3 คน) -> ปฏิเสธ แล้วบอกเหตุผลเฉพาะคนที่กด
        if (clients.size() < minPlayers) {
            h.send("MSG" + Protocol.SEP + "ต้องมีผู้เล่นอย่างน้อย " + minPlayers
                    + " คน (ตอนนี้ " + clients.size() + " คน)");
            return;
        }
        //> ตั้ง playing = true ก่อนสร้าง Thread (ข้างใน synchronized) กัน Thread อื่นเข้ามา start ซ้ำหรือ join เข้ามากลางทาง
        playing = true;
        //> คะแนนทุกคนกลับเป็น 0
        for (ClientHandler c : clients) {
            c.getPlayer().resetScore();
        }
        //> เปิด Thread ใหม่ ให้รัน run() ของห้องนี้ (new Thread(this) เพราะ GameRoom implements Runnable)
        Thread gameThread = new Thread(this);   // เริ่ม run() ใน Thread ใหม่
        //> start() ของ Thread = สั่งให้ run() เริ่มทำงานใน Thread ใหม่ทันที ส่วนเมธอด GameRoom.start() นี้จะจบต่อเลย (ไม่รอให้เกมจบ) ClientHandler จึงกลับไปรอรับข้อความต่อได้
        gameThread.start();
    }

    // ================= ลูปเกมหลัก (รันใน Thread แยก) =================

    //> run(): ลูปเกมทั้งเกม รันใน Thread เกมตัวเดียว
    //> ลำดับ: เริ่มเกม -> เล่นทีละรอบ (10 รอบ) -> ประกาศผู้ชนะ -> รอ 9 วินาที -> กลับห้องรอ
    public void run() {
        //> สร้างคลังคำ
        WordBank bank = new WordBank();
        //> สุ่มคำมา 10 คำ (ไม่ซ้ำ) หนึ่งคำต่อหนึ่งรอบ
        ArrayList<WordEntry> words = bank.pick(TOTAL_ROUNDS);

        //> ส่ง STATE#PLAYING ให้ทุกคนเปลี่ยนเป็นหน้าเกม
        beginGame();
        //> หน่วง 1.5 วินาที ให้ทุกจอเปลี่ยนหน้าทัน
        sleep(1500);
        //> วนรอบที่ 1 ถึง 10
        for (int r = 1; r <= words.size(); r++) {
            //> เล่น 1 รอบ (r = เลขรอบ, words.get(r - 1) = คำของรอบนั้น เพราะลิสต์เริ่มที่ 0)
            playRound(r, words.get(r - 1));
            //> ถ้าทุกคนออกหมดแล้วก็เลิกเกม
            if (!hasPlayers()) {
                break;                      // ทุกคนออกหมดแล้ว
            }
        }
        //> ประกาศผู้ชนะ ส่ง GAMEOVER
        announceWinner();
        //> โชว์หน้าสรุปผล 9 วินาที
        sleep(9000);
        //> กลับห้องรอ (ปลดล็อก playing = false) ให้เล่นใหม่ได้
        backToLobby();
    }

    //> playRound: 1 รอบ = เริ่มรอบ -> ทุก 1 วินาทีนับเวลาลด -> จบรอบ -> พักโชว์เฉลย
    /** เล่น 1 รอบ: เริ่ม -> นับเวลาถอยหลังทีละ 1 วินาที -> เฉลย */
    private void playRound(int roundNo, WordEntry entry) {
        //> ส่ง ROUND ให้ทุกคน และตั้งค่ารอบ (roundActive = true)
        startRound(roundNo, entry);
        //> วนจนกว่ารอบจะจบ (roundActive เป็น false จาก tick() เมื่อหมดเวลา หรือทุกคนตอบถูก)
        while (roundActive) {
            //> หลับ 1 วินาที  ***สำคัญ***: sleep ทำอยู่ 'นอก' synchronized ล็อกจึงว่าง ระหว่างนี้ Thread ของ Client เข้า handleGuess() ได้
            //> ถ้า sleep อยู่ในเมธอด synchronized จะไม่มีใครตอบได้เลยตลอด 1 วินาที
            sleep(1000);
            //> tick(): ลดเวลา 1 วินาที + แจ้ง Client + เช็กจบรอบ
            tick();
        }
        //> ส่ง ROUNDEND พร้อมเฉลย
        endRound();
        //> พัก 3.5 วินาทีให้ทุกคนดูเฉลย แล้ววนไปรอบถัดไป
        sleep(3500);                        // พักให้ดูเฉลย
    }

    //> beginGame: แจ้งเปลี่ยนหน้าเป็นหน้าเล่นเกม (synchronized เพราะ broadcast วนอ่านรายชื่อ clients ที่ Thread อื่นอาจแก้)
    private synchronized void beginGame() {
        broadcast("STATE" + Protocol.SEP + "PLAYING");
        broadcastScores();
    }

    //> hasPlayers: ยังมีคนอยู่ในห้องไหม
    private synchronized boolean hasPlayers() {
        return clients.size() > 0;
    }

    //> startRound: ตั้งค่ารอบใหม่
    private synchronized void startRound(int roundNo, WordEntry entry) {
        //> จำคำของรอบนี้
        current = entry;
        //> ถ้าทุกคนออกหมดไม่ต้องเริ่มรอบ
        if (clients.size() == 0) {
            roundActive = false;
            return;
        }
        //> แยกคำเป็นช่อง (ดู WordEntry.slots) เช่น "ค้างคาว" -> 6 ช่อง
        slots = entry.slots();
        //> revealed = อาร์เรย์ใหม่ขนาดเท่าจำนวนช่อง ทุกช่องเริ่มเป็น false (ยังปิด)
        revealed = new boolean[slots.size()];   // ค่าเริ่มต้นเป็น false ทั้งหมด
        //> รอบใหม่ ยังไม่มีใครตอบถูก -> คนแรกที่ถูกจะได้โบนัส
        firstSolver = true;
        //> รีเซ็ตเวลาเป็น 30 วินาที
        secondsLeft = ROUND_SECONDS;
        //> ล้างสถานะตอบถูกของทุกคน (เอาจุดเขียวออก)
        for (ClientHandler c : clients) {
            c.getPlayer().setSolved(false);
        }
        //> เปิดรอบ รับคำตอบได้
        roundActive = true;
        //> ส่ง ROUND#รอบ#ทั้งหมด#คำใบ้#วินาที#pattern ให้ทุกคน เช่น ROUND#1#10#ที่สูงใหญ่...#30#_,_,_
        //> ตอนนี้ pattern ทุกช่องเป็น _ (ยังไม่เปิด)
        broadcast("ROUND" + Protocol.SEP + roundNo + Protocol.SEP + TOTAL_ROUNDS
                + Protocol.SEP + entry.getHint() + Protocol.SEP + ROUND_SECONDS
                + Protocol.SEP + pattern());
        //> ส่งตารางคะแนน (ล้างจุดเขียว)
        broadcastScores();
    }

    //> tick: ถูกเรียกทุก 1 วินาทีโดย playRound
    /** ถูกเรียกทุก 1 วินาที */
    private synchronized void tick() {
        //> ถ้าทุกคนออกหมด จบรอบทันที
        if (clients.size() == 0) {
            roundActive = false;
            return;
        }
        //> ลดเวลา 1 วินาที
        secondsLeft--;
        //> ส่ง TICK#เวลาที่เหลือ ให้ทุกจอ (ทุกเครื่องจึงเห็นเวลาเท่ากัน เพราะ Server เป็นคนนับ)
        broadcast("TICK" + Protocol.SEP + secondsLeft);
        //> เหลือ 15 วินาที และ 8 วินาที (= ผ่านไป 15 และ 22 วินาที) เปิดตัวอักษรช่วยให้ 1 ช่อง
        if (secondsLeft == 15 || secondsLeft == 8) {
            revealOne();                    // เปิดตัวอักษรช่วย
        }
        //> จบรอบเมื่อ: หมดเวลา (secondsLeft <= 0) หรือ ทุกคนตอบถูกครบ (allSolved)
        if (secondsLeft <= 0 || allSolved()) {
            roundActive = false;            // หมดเวลา หรือทุกคนตอบถูกแล้ว
        }
    }

    //> endRound: ปิดรับคำตอบและเฉลย
    private synchronized void endRound() {
        roundActive = false;
        //> ROUNDEND#คำตอบ  (ตอนนี้ Client ถึงจะรู้คำตอบ)
        broadcast("ROUNDEND" + Protocol.SEP + current.getWord());
        broadcastScores();
    }

    //> announceWinner: หาผู้ชนะหลังครบรอบ
    private synchronized void announceWinner() {
        if (clients.size() == 0) {
            return;
        }
        //> best = คะแนนสูงสุด (เริ่ม -1 เพื่อให้คะแนน 0 ก็ชนะได้ถ้าทุกคนได้ 0)
        int best = -1;
        //> วนหาคะแนนสูงสุด
        for (ClientHandler c : clients) {
            if (c.getPlayer().getScore() > best) {
                best = c.getPlayer().getScore();
            }
        }
        //> winners = รายชื่อทุกคนที่ได้คะแนนเท่ากับสูงสุด (เสมอ = ประกาศทั้งหมด คั่นด้วย , )
        String winners = "";
        for (ClientHandler c : clients) {
            if (c.getPlayer().getScore() == best) {
                if (!winners.equals("")) {
                    winners = winners + ", ";
                }
                winners = winners + c.getPlayer().getName();
            }
        }
        //> ส่ง GAMEOVER#ชื่อ (คะแนน)
        broadcast("GAMEOVER" + Protocol.SEP + winners + " (" + best + " คะแนน)");
        broadcastScores();
    }

    //> backToLobby: กลับห้องรอ ให้เริ่มเกมใหม่ได้ คนใหม่เข้าห้องได้อีก
    private synchronized void backToLobby() {
        playing = false;
        broadcastState();
        broadcastScores();
    }

    // ================= การตอบคำถาม =================

    //> handleGuess: ตรวจคำตอบ (เรียกจาก ClientHandler เมื่อได้รับ GUESS#คำตอบ)
    //> synchronized: กันกรณีสองคนกดตอบถูกพร้อมกัน ให้ทีละคนเท่านั้น (โบนัสคนแรกจึงตกที่คนเดียว)
    public synchronized void handleGuess(ClientHandler h, String text) {
        //> ไม่ได้อยู่ในเกม หรือไม่ได้อยู่ในช่วงรอบ (เช่นช่วงพักเฉลย) -> ไม่รับคำตอบ
        if (!playing || !roundActive) {
            return;
        }
        //> p = ผู้เล่นที่ตอบ
        Player p = h.getPlayer();
        //> ตอบถูกไปแล้วในรอบนี้ -> ไม่นับซ้ำ
        if (p.isSolved()) {
            return;                         // ตอบถูกไปแล้ว ตอบซ้ำไม่ได้
        }
        //> เทียบคำตอบหลังตัดช่องว่าง/ทำตัวพิมพ์เล็ก ถ้าตรงกัน = ถูก
        if (normalize(text).equals(normalize(current.getWord()))) {
            //> คะแนน = 10 + เวลาที่เหลือ x 3  (ตอบเร็ว ได้เยอะ) เช่นเหลือ 20 วินาที = 70 คะแนน
            int points = 10 + secondsLeft * 3;      // ตอบเร็ว = คะแนนเยอะ (สูงสุด 100)
            //> โบนัสคนแรกที่ตอบถูก +10
            if (firstSolver) {
                points = points + 10;               // โบนัสคนแรก
                firstSolver = false;
            }
            //> บวกคะแนนเข้า Player (เก็บที่ Server เท่านั้น)
            p.addScore(points);
            //> ทำเครื่องหมายว่ารอบนี้ตอบถูกแล้ว
            p.setSolved(true);
            //> ประกาศให้ทุกคนเห็น ตอบถูก + คะแนนที่ได้
            broadcast("MSG" + Protocol.SEP + p.getName() + " ตอบถูก! +" + points + " คะแนน");
            //> ส่งตารางคะแนนใหม่ (จุดเขียวหน้าชื่อ + คะแนนอัปเดต)
            broadcastScores();
        } else {
            //> ตอบผิด: ประกาศ 'ชื่อ ตอบผิด' (ไม่บอกว่าพิมพ์อะไร) แล้วตอบใหม่ได้ไม่จำกัดจนหมดเวลา
            broadcast("MSG" + Protocol.SEP + p.getName() + " ตอบผิด");
        }
    }

    //> normalize: ตัดช่องว่างทั้งหมด + ตัวพิมพ์เล็ก  เพื่อให้ "ช้าง " หรือ "Tiger"/"tiger" ถือว่าตรงกัน
    /** ตัดช่องว่างและทำเป็นตัวพิมพ์เล็ก เพื่อเทียบคำตอบ */
    private String normalize(String text) {
        return text.replace(" ", "").trim().toLowerCase();
    }

    //> allSolved: ทุกคนตอบถูกแล้วหรือยัง
    private boolean allSolved() {
        for (ClientHandler c : clients) {
            if (!c.getPlayer().isSolved()) {
                return false;
            }
        }
        //> ต้องมีคนอยู่ในห้องด้วย (ห้องว่างไม่ถือว่าตอบครบ)
        return clients.size() > 0;
    }

    // ================= คำใบ้ช่วย (เปิดตัวอักษร) =================

    //> revealOne: เปิดตัวอักษรช่วย 1 ช่อง (ที่สุ่มจากช่องที่ยังปิด)
    private void revealOne() {
        //> hidden = ลิสต์ตำแหน่งช่องที่ยังปิด
        ArrayList<Integer> hidden = new ArrayList<>();      // ช่องที่ยังปิดอยู่
        for (int i = 0; i < revealed.length; i++) {
            if (!revealed[i]) {
                hidden.add(i);
            }
        }
        //> ถ้าเหลือช่องปิดแค่ 1 ช่อง ไม่เปิดเพิ่ม (ให้เดาเอง ไม่งั้นได้คำตอบฟรี)
        if (hidden.size() <= 1) {
            return;                         // เหลือช่องสุดท้ายให้ผู้เล่นเดาเอง
        }
        //> Random = ตัวสุ่ม
        Random random = new Random();
        //> สุ่มเลือก 1 ตำแหน่งจากช่องที่ยังปิด
        int choice = hidden.get(random.nextInt(hidden.size()));
        //> ทำเครื่องหมายว่าช่องนั้นเปิดแล้ว
        revealed[choice] = true;
        //> ส่ง HINT#pattern ใหม่ให้ทุกคน (Client จะวาดตัวอักษรในช่องนั้น)
        broadcast("HINT" + Protocol.SEP + pattern());
        broadcast("MSG" + Protocol.SEP + "ระบบเปิดตัวอักษรช่วย 1 ช่อง");
    }

    //> pattern: แปลงสถานะช่องเป็นข้อความที่ส่งไป Client เช่น "ค,_,า,_" (_ = ยังปิดอยู่ , = คั่นช่อง)
    /** สร้างข้อความช่องตัวอักษร เช่น "ค,_,า,_" (_ = ยังปิดอยู่) */
    private String pattern() {
        //> result = ข้อความที่กำลังสร้าง
        String result = "";
        for (int i = 0; i < slots.size(); i++) {
            //> ใส่ , คั่นระหว่างช่อง (ไม่ใส่หน้าช่องแรก)
            if (i > 0) {
                result = result + ",";
            }
            //> ช่องเปิดแล้ว -> ใส่ตัวอักษรจริง
            if (revealed[i]) {
                result = result + slots.get(i);
            } else {
                //> ช่องยังปิด -> ใส่ _
                result = result + "_";
            }
        }
        return result;
    }

    // ================= ส่งข้อมูลให้ทุกคน =================

    //> broadcast: ส่งข้อความเดียวกันให้ 'ทุกคน' ในห้อง (วน send ทีละ ClientHandler)
    //> เรียกเฉพาะตอนถือล็อก (อยู่ในเมธอด synchronized) ข้อความจากหลาย Thread จึงไม่สลับปนกัน
    private void broadcast(String line) {
        for (ClientHandler c : clients) {
            c.send(line);
        }
    }

    //> broadcastState: ส่งสถานะห้องรอ  STATE#LOBBY#ขั้นต่ำ#ชื่อหัวหน้า (หัวหน้า = คนตำแหน่ง 0)
    private void broadcastState() {
        String host = "";
        if (clients.size() > 0) {
            host = clients.get(0).getPlayer().getName();
        }
        broadcast("STATE" + Protocol.SEP + "LOBBY" + Protocol.SEP + minPlayers
                + Protocol.SEP + host);
    }

    //> broadcastScores: ส่งตารางคะแนนเรียงจากมากไปน้อย (selection sort) รูปแบบ SCORES#ชื่อ:คะแนน:ตอบแล้ว;ชื่อ:...
    private void broadcastScores() {
        //> เรียง 'สำเนา' ของลิสต์ผู้เล่นด้วย selection sort: รอบที่ i หาตัวที่คะแนนมากที่สุดจากตำแหน่ง i เป็นต้นไป แล้วสลับมาไว้ตำแหน่ง i
        //> (ไม่เรียงลิสต์ clients ตรงๆ เพราะลำดับใน clients = ลำดับเข้าห้อง ใช้หาหัวหน้า)
        // คัดลอกผู้เล่นแล้วเรียงจากคะแนนมากไปน้อย (selection sort)
        ArrayList<Player> list = new ArrayList<>();
        for (ClientHandler c : clients) {
            list.add(c.getPlayer());
        }
        for (int i = 0; i < list.size(); i++) {
            int maxIndex = i;
            for (int j = i + 1; j < list.size(); j++) {
                if (list.get(j).getScore() > list.get(maxIndex).getScore()) {
                    maxIndex = j;
                }
            }
            Player temp = list.get(i);
            list.set(i, list.get(maxIndex));
            list.set(maxIndex, temp);
        }

        //> สร้างข้อความ SCORES ทีละคน
        String text = "";
        for (int i = 0; i < list.size(); i++) {
            Player p = list.get(i);
            //> คั่นผู้เล่นด้วย ;
            if (i > 0) {
                text = text + ";";
            }
            //> solvedFlag: 1 = ตอบถูกแล้วรอบนี้ 0 = ยัง
            int solvedFlag = 0;
            if (p.isSolved()) {
                solvedFlag = 1;
            }
            //> รูปแบบของผู้เล่น 1 คน เช่น "สมชาย:107:1"
            text = text + p.getName() + ":" + p.getScore() + ":" + solvedFlag;
        }
        broadcast("SCORES" + Protocol.SEP + text);
    }

    //> sleep: หยุด Thread ปัจจุบัน (ไม่ใช่ทั้งโปรแกรม) ตามเวลา มิลลิวินาที 1000 = 1 วินาที
    //> ต้อง try-catch เพราะ Thread.sleep อาจโยน InterruptedException (ไม่เกิดในโปรแกรมนี้ จึงไม่ต้องทำอะไร)
    private void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            // ไม่ต้องทำอะไร
        }
    }
}
