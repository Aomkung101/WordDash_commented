//> [ไฟล์นี้ทำอะไร] จุดเริ่มต้นของฝั่ง Server (มี main) รันด้วย: java GameServer [port] [ผู้เล่นขั้นต่ำ]
//> เปิดพอร์ต รอ Client เชื่อมเข้ามา และสร้าง Thread + ClientHandler ให้ Client แต่ละคน
//> สร้าง GameRoom แค่ 1 ห้อง ใช้ร่วมกันทุกคน
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Server ของ WORD DASH
 * รัน:  java GameServer [port] [จำนวนผู้เล่นขั้นต่ำ]   (ค่าเริ่มต้น 5000 และ 3)
 */
public class GameServer {
    //> main: ที่แรกที่ Java เริ่มทำงาน   args = ค่าที่พิมพ์ต่อท้ายคำสั่ง
    //> เช่น run_server.bat 5000 1  ->  args[0] = "5000", args[1] = "1"
    public static void main(String[] args) {
        //> ค่าเริ่มต้น พอร์ต 5000 (ถ้าไม่ระบุ)
        int port = 5000;
        //> ค่าเริ่มต้น ต้องมีผู้เล่นอย่างน้อย 3 คนถึงเริ่มเกมได้
        int minPlayers = 3;
        //> args.length > 0 = มีการพิมพ์ค่าต่อท้ายอย่างน้อย 1 ตัว
        if (args.length > 0) {
            //> Integer.parseInt = แปลงข้อความ "5000" เป็นตัวเลข 5000
            //> (ถ้าพิมพ์ไม่ใช่ตัวเลขจะเกิด error NumberFormatException)
            port = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            minPlayers = Integer.parseInt(args[1]);
        }

        //> สร้างห้องเกมแค่ 1 ห้อง ส่งให้ทุก ClientHandler ใช้ร่วมกัน
        //> (นี่คือเหตุผลที่เมธอดใน GameRoom ต้อง synchronized)
        GameRoom room = new GameRoom(minPlayers);
        try {
            //> ServerSocket = เปิดพอร์ตเพื่อ 'รอ' การเชื่อมต่อ
            //> ถ้าพอร์ตถูกใช้อยู่แล้ว (เช่นเปิด Server ซ้ำ) จะโยน IOException ไปที่ catch ด้านล่าง
            ServerSocket server = new ServerSocket(port);
            System.out.println("WORD DASH server started on port " + port
                    + " (min players: " + minPlayers + ")");
            //> ลูปไม่รู้จบ: Server ต้องรอรับ Client ใหม่เรื่อยๆ
            while (true) {
                //> accept() = หยุดรอจนกว่าจะมี Client เชื่อมเข้ามา แล้วคืน Socket ของคนนั้น
                Socket socket = server.accept();                    // รอ Client เข้ามา
                //> สร้างพนักงานประจำตัวให้ Client คนนี้ พร้อมส่ง socket และห้อง
                ClientHandler handler = new ClientHandler(socket, room);
                //> สร้าง Thread ใหม่ที่จะรัน handler.run()
                //> หลักการ: 1 Thread ต่อ 1 Client เพื่อให้หลายคนคุยพร้อมกันได้
                Thread thread = new Thread(handler);                // 1 Thread ต่อ 1 Client
                //> start() = เริ่ม Thread (ไม่ใช่เรียก run() ตรงๆ) แล้ว main ก็วนกลับไป accept() รอคนต่อไปทันที
                thread.start();
            }
        } catch (IOException e) {
            //> พอร์ตใช้ไม่ได้ (ถูกใช้อยู่/ไม่มีสิทธิ์) แสดงข้อความแล้วโปรแกรมจบ
            System.out.println("Cannot start server: " + e.getMessage());
        }
    }
}
