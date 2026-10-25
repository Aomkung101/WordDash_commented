//> [ไฟล์นี้ทำอะไร] 'พนักงานประจำตัว' ของ Client 1 เครื่องฝั่ง Server
//> GameServer สร้าง ClientHandler 1 ตัว + 1 Thread ต่อ 1 Client ที่เชื่อมเข้ามา
//> หน้าที่: อ่านข้อความที่ Client ส่งมา (JOIN / GUESS / START / LEAVE) แล้วส่งต่อให้ GameRoom ตัดสิน
//> และมีเมธอด send() ให้ GameRoom ใช้ส่งข้อความกลับไปหา Client คนนี้
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

/** ดูแล Client 1 เครื่อง (รันใน Thread ของตัวเอง) */
//> implements Runnable = คลาสนี้ถูกรันใน Thread ได้ โดยโค้ดที่รันคือเมธอด run() ด้านล่าง
public class ClientHandler implements Runnable {
    //> socket = ท่อเชื่อมต่อ TCP กับ Client เครื่องนี้
    private Socket socket;
    //> room = ห้องเกมเดียวที่ทุก ClientHandler ใช้ร่วมกัน (ส่งมาจาก GameServer)
    private GameRoom room;
    //> out = ตัวเขียนข้อความไปหา Client
    private PrintWriter out;
    private Player player;     // null จนกว่าจะ JOIN สำเร็จ

    //> Constructor: GameServer เป็นคนเรียก ส่ง socket ที่ accept มาและห้องเกม
    public ClientHandler(Socket socket, GameRoom room) {
        this.socket = socket;
        this.room = room;
    }

    //> getter/setter ของ player: GameRoom ใช้ตั้ง Player ตอน join และอ่านชื่อ/คะแนนทีหลัง
    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }

    /** ส่งข้อความ 1 บรรทัดไปหา Client */
    //> send: ส่งข้อความ 1 บรรทัดไปหา Client คนนี้
    //> synchronized = ให้ทำได้ทีละ Thread เพราะ Thread เกม (GameRoom) กับ Thread ของ Client อาจเรียก send พร้อมกัน
    //> ถ้าไม่ล็อก ข้อความ 2 อันอาจปนกันเป็นบรรทัดเดียว
    public synchronized void send(String line) {
        //> out ยังเป็น null ได้ถ้า run() ยังไม่ทันสร้าง จึงต้องเช็ก
        if (out != null) {
            //> println = เขียนข้อความ + ขึ้นบรรทัดใหม่ ('\n' = สัญญาณบอกว่าข้อความจบ 1 ข้อความ)
            //> ฝั่งรับใช้ readLine() อ่านทีละบรรทัดพอดี
            out.println(line);
        }
    }

    //> close: ปิดการเชื่อมต่อ (เรียกตอน Client ออก หรือเข้าห้องไม่ได้)
    public void close() {
        try {
            //> socket.close() อาจโยน IOException ต้องมี try-catch
            socket.close();
        } catch (IOException e) {
            // ไม่ต้องทำอะไร
        }
    }

    //> run(): ทำงานใน Thread ของ Client คนนี้คนเดียว
    //> ลูปหลักคือ 'รอรับข้อความ -> ส่งให้ห้อง -> รอข้อความต่อไป' จนกว่า Client จะหลุดหรือส่ง LEAVE
    //> เพราะแต่ละ Client มี Thread เป็นของตัวเอง การรออ่านของคนหนึ่งจึงไม่บล็อกคนอื่น
    public void run() {
        try {
            //> ต่อท่ออ่านเป็นทอดๆ:
            //>  socket.getInputStream() = ไบต์ดิบที่ Client ส่งมา
            //>  InputStreamReader(..., "UTF-8") = แปลงไบต์เป็นตัวอักษร (ต้องระบุ UTF-8 ไม่งั้นภาษาไทยเพี้ยน)
            //>  BufferedReader = ให้เรียก readLine() อ่านทีละบรรทัดได้
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            //> ต่อท่อเขียน: PrintWriter(..., true) ตัวที่สอง true = autoFlush
            //> หมายถึงส่งออกทันทีที่ println (ถ้าไม่ flush ข้อความจะค้างในบัฟเฟอร์ Client ไม่เห็น)
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            //> readLine() จะ 'หยุดรอ' (blocking) จนกว่า Client ส่งมา 1 บรรทัด
            //> ถ้า Client ตัดการเชื่อมต่อ จะคืนค่า null
            String line = in.readLine();
            //> วนต่อตราบที่ยังไม่ null (null = Client หลุด)
            while (line != null) {
                //> แยกบรรทัดเป็น คำสั่ง กับ ข้อมูล เช่น "GUESS#ช้าง" -> command = GUESS, argument = ช้าง
                String command = Protocol.getCommand(line);
                String argument = Protocol.getArgument(line);

                //> == JOIN: Client ขอเข้าห้อง (ข้อมูล = ชื่อที่พิมพ์) ==
                if (command.equals("JOIN")) {
                    //> player == null = ยังไม่เคยเข้าห้อง (กัน JOIN ซ้ำ)
                    if (player == null) {
                        //> room.join(...) คืน true ถ้าเข้าได้ / false ถ้าห้องเต็มหรือเกมกำลังเล่น
                        boolean ok = room.join(this, argument);
                        if (!ok) {
                            //> break = ออกจากลูป while -> ไปปิดการเชื่อมต่อท้ายเมธอด
                            break;              // เข้าห้องไม่ได้ -> เลิกคุยกับ Client นี้
                        }
                    }
                } else if (command.equals("GUESS")) {
                    //> == GUESS: Client ส่งคำตอบ ==
                    //> ต้องเข้าห้องแล้ว (player != null) ถึงจะรับคำตอบ
                    if (player != null) {
                        //> ส่งให้ห้องตรวจ: ห้องเป็นผู้ตัดสินถูก/ผิดและให้คะแนน
                        room.handleGuess(this, argument);
                    }
                } else if (command.equals("START")) {
                    //> == START: ขอเริ่มเกม ==  ห้องจะเช็กเองว่าเป็นหัวหน้าห้องและคนครบไหม
                    if (player != null) {
                        room.start(this);
                    }
                } else if (command.equals("LEAVE")) {
                    //> == LEAVE: Client บอกว่าจะออก == ออกจากลูป
                    break;
                }
                //> อ่านบรรทัดถัดไป (วนกลับไปตรวจเงื่อนไข while)
                line = in.readLine();           // อ่านบรรทัดถัดไป (null = Client หลุด)
            }
        } catch (IOException e) {
            //> IOException = การเชื่อมต่อพัง เช่น ปิดหน้าต่างเกมกระทันหัน ไม่ต้องทำอะไรเพิ่ม
            // Client หลุดการเชื่อมต่อ
        }
        //> ไม่ว่าจะออกด้วยวิธีไหน (LEAVE / หลุด / เข้าห้องไม่ได้) ต้องบอกห้องว่าคนนี้ออกแล้ว
        //> (leave() เช็กเองว่าเคยเข้าห้องไหม ถ้าไม่เคยจะไม่ทำอะไร)
        room.leave(this);
        //> ปิด socket ให้เรียบร้อย
        close();
    }
}
