//> [ไฟล์นี้ทำอะไร] 'กระดานประกาศ' ฝั่ง Client: จำสิ่งที่ Server ส่งมาล่าสุด (ช่วงเวลา, คำใบ้, ช่องตัวอักษร, คะแนน ฯลฯ)
//> Client ไม่คำนวณเกมเอง!  NetworkClient (Thread รับข้อมูล) เขียนลงที่นี่ผ่าน apply()
//> GamePanel (Thread หน้าจอ) อ่านจากที่นี่ผ่าน getter แล้ววาด ==> ทุกเครื่องเห็นเหมือนกันเพราะอ่านจากแหล่งเดียวกัน (Server)
import java.util.ArrayList;

/**
 * สถานะเกมฝั่ง Client (เหมือน "กระดานประกาศ")
 * Client ไม่คำนวณเกมเอง แค่จำสิ่งที่ Server ส่งมาล่าสุด แล้วให้ GamePanel อ่านไปวาด
 * จึงทำให้ทุกเครื่องเห็นหน้าจอเหมือนกัน
 *
 * ทุกเมธอดเป็น synchronized เพราะมี 2 Thread ใช้พร้อมกัน:
 *   Thread รับข้อมูลจากเครือข่าย (เขียน)  และ  Thread ของหน้าจอ (อ่านไปวาด)
 */
public class GameState {
    //> phase = หน้าจอที่ต้องแสดง: CONNECTING (ยังไม่ต่อ/หลุด), LOBBY (ห้องรอ), PLAYING (กำลังเล่น), GAMEOVER (จบเกม)
    //> GamePanel.paintComponent เลือกวาดตามค่านี้
    private String phase = "CONNECTING";    // CONNECTING, LOBBY, PLAYING, GAMEOVER
    //> connected = เข้าห้องสำเร็จหรือยัง (true หลังได้รับ WELCOME)
    private boolean connected = false;
    //> errorShown = Server ส่ง ERROR มาแล้วหรือยัง (เช่น ห้องเต็ม) ใช้กันไม่ให้ข้อความ error ถูกทับด้วย 'ขาดการเชื่อมต่อ'
    private boolean errorShown = false;
    //> myName = ชื่อของฉันที่ Server ยืนยัน (ใช้ไฮไลต์แถวของตัวเอง และเช็กว่าเป็นหัวหน้าห้องไหม)
    private String myName = "";
    //> host = ชื่อหัวหน้าห้อง (ผู้เล่นคนแรกที่เข้าห้อง)
    private String host = "";
    //> minPlayers = จำนวนผู้เล่นขั้นต่ำที่ Server กำหนด (ไว้โชว์ในห้องรอ)
    private int minPlayers = 3;

    //> ข้อมูลรอบปัจจุบัน: round = รอบที่เท่าไร
    private int round = 0;
    //> totalRounds = ทั้งหมดกี่รอบ (10)
    private int totalRounds = 0;
    //> remaining = วินาทีที่เหลือ (Server ส่ง TICK มาทุก 1 วินาที) ฝั่ง Client แค่เก็บค่านี้ไปแสดง ไม่ได้นับเวลาเอง
    private int remaining = 0;              // วินาทีที่เหลือ
    //> hint = คำใบ้
    private String hint = "";
    //> pattern = ช่องตัวอักษร  "_" = ยังปิดอยู่ เช่น ["_", "า", "_"]
    private ArrayList<String> pattern = new ArrayList<>();   // ช่องตัวอักษร ("_" = ยังปิด)
    //> roundOver = รอบนี้จบแล้วและกำลังโชว์เฉลย
    private boolean roundOver = false;
    //> answer = คำเฉลย (มีค่าหลัง ROUNDEND เท่านั้น ช่วงเล่นเป็น "" เพื่อกันโกง Client ไม่รู้คำตอบเลย)
    private String answer = "";
    //> winner = ข้อความผู้ชนะ (ใช้ตอน GAMEOVER)
    private String winner = "";

    //> status = ข้อความสถานะมุมล่างขวา
    private String status = "กำลังเชื่อมต่อ...";
    //> lastMsg = ข้อความล่าสุดจากระบบ เช่น "สมชาย ตอบถูก! +107 คะแนน" (โชว์ที่ด้านล่างจอ)
    private String lastMsg = "";
    //> players = ตารางคะแนนล่าสุด (เรียงจากมากไปน้อยมาจาก Server แล้ว)
    private ArrayList<PlayerInfo> players = new ArrayList<>();

    /** รับข้อความ 1 บรรทัดจาก Server แล้วอัปเดตตัวแปรตามชนิดของคำสั่ง */
    //> apply: รับข้อความ 1 บรรทัดจาก Server แล้วอัปเดตตัวแปรให้ตรงกับคำสั่ง
    //> synchronized = ล็อกไว้ให้ทำทีละ Thread เพราะ Thread รับข้อมูล (เขียน) กับ Thread หน้าจอ (อ่าน) ใช้ object นี้พร้อมกัน
    //> ถ้าไม่ล็อก หน้าจออาจอ่านค่าครึ่งๆ กลางๆ ตอนกำลังอัปเดต
    public synchronized void apply(String line) {
        //> command = ชื่อคำสั่ง เช่น "TICK" 
        String command = Protocol.getCommand(line);
        //> argument = ข้อมูลที่ตามมา เช่น "12" 
        String argument = Protocol.getArgument(line);
        //> split("#") = หั่นข้อมูลที่คั่นด้วย # เป็นช่องๆ ใส่อาร์เรย์ p
        //> เช่น ROUND ส่งมา "3#10#คำใบ้#30#ค,_,า" -> p[0]="3", p[1]="10", p[2]="คำใบ้", p[3]="30", p[4]="ค,_,า"
        String[] p = argument.split(Protocol.SEP);   // แยกข้อมูลเป็นช่องๆ

        //> ไล่ตรวจชนิดคำสั่งทีละอัน (เหมือน switch) ทุกคำสั่งที่ Server ส่งมาจะเข้ามาที่นี่
        if (command.equals("WELCOME")) {
            //> WELCOME#ชื่อ  = Server ยืนยันว่าเข้าห้องได้ ชื่อที่ได้อาจถูกต่อเลขท้ายถ้าซ้ำกับคนอื่น
            myName = argument;
            connected = true;

        } else if (command.equals("STATE")) {
            //> STATE#LOBBY#ขั้นต่ำ#หัวหน้า  หรือ  STATE#PLAYING = Server บอกว่าตอนนี้อยู่หน้าไหน
            //> p[0] คือชื่อหน้า
            phase = p[0];
            //> ถ้าเป็นห้องรอ ข้อมูลจะมีเพิ่ม: p[1] = ผู้เล่นขั้นต่ำ, p[2] = ชื่อหัวหน้าห้อง
            if (phase.equals("LOBBY")) {
                //> แปลงข้อความเป็นตัวเลข
                minPlayers = Integer.parseInt(p[1]);
                //> เช็กความยาวก่อน กัน error ถ้าไม่มีชื่อหัวหน้า (split ตัดช่องว่างท้ายทิ้ง)
                if (p.length > 2) {
                    host = p[2];
                } else {
                    host = "";
                }
                //> กลับเข้าห้องรอ = ล้างข้อมูลเกมเก่าทิ้งทั้งหมด (รอบ คำใบ้ ช่องตัวอักษร)
                roundOver = false;
                round = 0;
                hint = "";
                pattern = new ArrayList<>();
                status = "รอผู้เล่น";
            } else {
                status = "กำลังเล่น";
            }

        } else if (command.equals("SCORES")) {
            //> SCORES#ชื่อ:คะแนน:ตอบแล้ว;ชื่อ:... = ตารางคะแนนใหม่ (ส่งทุกครั้งที่คะแนนหรือสมาชิกเปลี่ยน)
            players = parseScores(argument);

        } else if (command.equals("ROUND")) {
            //> ROUND#รอบ#ทั้งหมด#คำใบ้#วินาที#pattern = เริ่มรอบใหม่ Server ส่งมาทีเดียวครบทุกอย่างที่จอต้องใช้
            phase = "PLAYING";
            round = Integer.parseInt(p[0]);
            totalRounds = Integer.parseInt(p[1]);
            hint = p[2];
            remaining = Integer.parseInt(p[3]);
            pattern = parsePattern(p[4]);
            //> เคลียร์สถานะรอบเก่า (ซ่อนเฉลย เปิดช่องพิมพ์)
            roundOver = false;
            answer = "";
            status = "กำลังเล่น";

        } else if (command.equals("HINT")) {
            //> HINT#pattern = Server เปิดตัวอักษรช่วย 1 ช่อง (ส่ง pattern ใหม่ทั้งชุด)
            pattern = parsePattern(argument);

        } else if (command.equals("TICK")) {
            //> TICK#วินาที = Server นับถอยหลังแล้วบอกทุก 1 วินาที  Client แค่เอาไปแสดง
            remaining = Integer.parseInt(argument);

        } else if (command.equals("MSG")) {
            //> MSG#ข้อความ = ข้อความแจ้งเหตุการณ์ เช่น ใครเข้าห้อง ตอบถูก/ผิด
            lastMsg = argument;

        } else if (command.equals("ROUNDEND")) {
            //> ROUNDEND#เฉลย = หมดเวลา/ตอบครบแล้ว Server เฉลยคำตอบ (เพิ่งรู้คำตอบตอนนี้)
            roundOver = true;
            answer = argument;
            remaining = 0;
            status = "เฉลย: " + argument;

        } else if (command.equals("GAMEOVER")) {
            //> GAMEOVER#ข้อความผู้ชนะ = จบ 10 รอบ ไปหน้าสรุปผล
            phase = "GAMEOVER";
            winner = argument;

        } else if (command.equals("ERROR")) {
            //> ERROR#ข้อความ = Server ปฏิเสธ (ห้องเต็ม/เกมกำลังเล่น) แล้วจะตัดการเชื่อมต่อเอง
            status = argument;
            lastMsg = argument;
            connected = false;
            errorShown = true;
        }
    }

    /** แปลง "A:110:1;B:0:0" เป็นลิสต์ของ PlayerInfo */
    //> แปลง "A:110:1;B:0:0" -> ลิสต์ PlayerInfo   (; คั่นผู้เล่น, : คั่นชื่อ/คะแนน/ตอบแล้ว)
    //> นี่คือเหตุผลที่ GameRoom.cleanName ต้องตัด ; : ออกจากชื่อ
    private ArrayList<PlayerInfo> parseScores(String text) {
        ArrayList<PlayerInfo> list = new ArrayList<>();
        //> ไม่มีผู้เล่น (ข้อความว่าง) -> คืนลิสต์ว่าง
        if (text.equals("")) {
            return list;
        }
        //> หั่นด้วย ; ได้ผู้เล่นทีละคน เช่น ["A:110:1", "B:0:0"]
        String[] items = text.split(";");
        for (int i = 0; i < items.length; i++) {
            //> หั่นด้วย : ได้ f[0]=ชื่อ f[1]=คะแนน f[2]=ตอบแล้ว(1/0)
            String[] f = items[i].split(":");
            String name = f[0];
            int score = Integer.parseInt(f[1]);
            //> "1" = ตอบถูกแล้วในรอบนี้ (ใช้วาดจุดเขียวหน้าชื่อ)
            boolean solved = f[2].equals("1");
            list.add(new PlayerInfo(name, score, solved));
        }
        return list;
    }

    //> แปลง "ค,_,า" -> ["ค", "_", "า"]  (ช่องที่ยังปิดคือ "_")
    /** แปลง "ค,_,า" เป็นลิสต์ ["ค", "_", "า"] */
    private ArrayList<String> parsePattern(String text) {
        ArrayList<String> list = new ArrayList<>();
        if (text.equals("")) {
            return list;
        }
        //> หั่นด้วยเครื่องหมายจุลภาค
        String[] parts = text.split(",");
        for (int i = 0; i < parts.length; i++) {
            list.add(parts[i]);
        }
        return list;
    }

    //> connectionLost: NetworkClient เรียกเมื่อขาดการเชื่อมต่อ -> กลับหน้า CONNECTING และขึ้นข้อความ
    //> (ถ้ามี ERROR จาก Server อยู่แล้ว เช่น 'ห้องเต็ม' จะไม่เขียนทับ)
    /** ถูกเรียกเมื่อการเชื่อมต่อกับ Server ขาด */
    public synchronized void connectionLost() {
        connected = false;
        phase = "CONNECTING";
        if (!errorShown) {
            status = "ขาดการเชื่อมต่อจาก Server";
        }
    }

    //> Getter ทั้งหมด synchronized: GamePanel (Thread หน้าจอ) เรียกอ่านค่าไปวาดทุก 33 ms
    // ---------- getter: ให้ GamePanel อ่านค่าไปวาด ----------
    public synchronized String getPhase() { return phase; }
    public synchronized boolean isConnected() { return connected; }
    public synchronized String getMyName() { return myName; }
    public synchronized String getHost() { return host; }
    public synchronized int getMinPlayers() { return minPlayers; }
    public synchronized int getRound() { return round; }
    public synchronized int getTotalRounds() { return totalRounds; }
    public synchronized int getRemaining() { return remaining; }
    public synchronized String getHint() { return hint; }
    public synchronized boolean isRoundOver() { return roundOver; }
    public synchronized String getAnswer() { return answer; }
    public synchronized String getWinner() { return winner; }
    public synchronized String getStatus() { return status; }
    public synchronized String getLastMsg() { return lastMsg; }

    //> คืน 'สำเนา' ของลิสต์ เพื่อไม่ให้ Thread อื่นแก้ลิสต์ตอนที่ GamePanel กำลังวนวาดอยู่
    //> (ถ้าส่งตัวจริงไป แล้ว apply() เปลี่ยนลิสต์กลางทาง อาจ error: ConcurrentModificationException)
    // คืนสำเนาของลิสต์ เพื่อไม่ให้ Thread อื่นแก้ลิสต์ตอนที่กำลังวาดอยู่
    public synchronized ArrayList<String> getPattern() {
        return new ArrayList<>(pattern);
    }

    public synchronized ArrayList<PlayerInfo> getPlayers() {
        return new ArrayList<>(players);
    }
}
