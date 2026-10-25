//> [ไฟล์นี้ทำอะไร] ตัวเชื่อมต่อ Server ฝั่ง Client: เปิด Socket, ส่งคำสั่ง, และมี Thread คอยรับข้อความจาก Server
//> ข้อความที่รับได้จะส่งให้ GameState เก็บไว้ (ไม่แตะหน้าจอตรงๆ)
//> GameFrame เรียก connect() ตอนเปิดเกม, send() ตอนกดปุ่ม, close() ตอนออก
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * จัดการการเชื่อมต่อกับ Server
 * implements Runnable: เมธอด run() คือ Thread ที่คอยอ่านข้อความจาก Server ไม่หยุด
 * แล้วส่งให้ GameState เก็บไว้
 */
//> implements Runnable: run() ด้านล่างคือโค้ดที่รันใน Thread รับข้อมูล
public class NetworkClient implements Runnable {
    //> state = กระดานสถานะเกมที่ใช้ร่วมกับ GamePanel (ส่งมาจาก GameFrame)
    private GameState state;
    //> socket/in/out = ท่อเชื่อมกับ Server (in = อ่าน, out = เขียน)
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    //> Constructor: GameFrame สร้าง NetworkClient โดยส่ง state เข้ามา
    public NetworkClient(GameState state) {
        this.state = state;
    }

    //> connect: เชื่อมต่อ Server  throws IOException = ถ้าเชื่อมไม่ได้ (IP ผิด/Server ไม่เปิด/ไฟร์วอลล์) จะโยน error ไปให้ผู้เรียกจัดการ
    //> ซึ่ง GameFrame.main จะจับแล้วโชว์กล่อง 'เชื่อมต่อ Server ไม่ได้'
    public void connect(String host, int port, String name) throws IOException {
        //> new Socket(host, port) = ต่อ TCP ไปที่ IP:พอร์ตของ Server ตรงนี้ถ้า Server ไม่ตอบจะค้าง/ล้มเหลว
        socket = new Socket(host, port);
        //> ท่ออ่านจาก Server (UTF-8 + อ่านทีละบรรทัด) เหมือนฝั่ง ClientHandler
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        //> ท่อเขียนไป Server (true = ส่งทันทีเมื่อ println)
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        //> ส่ง JOIN#ชื่อ ทันทีที่ต่อติด = ขอเข้าห้อง
        send("JOIN" + Protocol.SEP + name);

        //> ทำไมต้องมี Thread แยก: readLine() 'หยุดรอ' จนกว่าจะมีข้อความ
        //> ถ้ารอบนเธรดหน้าจอ หน้าต่างจะค้างทั้งหมด จึงให้ Thread นี้รอข้อความอยู่เบื้องหลัง
        //> new Thread(this) = ให้รัน run() ของ object นี้
        Thread reader = new Thread(this);   // เริ่ม run() ใน Thread ใหม่
        //> start() = เริ่ม Thread
        reader.start();
    }

    /** Thread รับข้อมูลจาก Server */
    //> run(): ทำงานใน Thread รับข้อมูล วนรอฟัง Server ไปเรื่อยๆ
    public void run() {
        try {
            //> อ่านบรรทัดแรก (หยุดรอจนกว่า Server ส่งมา)
            String line = in.readLine();
            //> null = Server ปิดการเชื่อมต่อ
            while (line != null) {
                //> ส่งบรรทัดที่ได้ให้ GameState แปลความหมายแล้วเก็บ (เช่น TICK#12 -> remaining = 12)
                //> Thread นี้แค่ 'อัปเดตข้อมูล' ไม่วาดหน้าจอ
                state.apply(line);
                //> อ่านบรรทัดถัดไป
                line = in.readLine();
            }
        } catch (IOException e) {
            //> การเชื่อมต่อขาด ไม่ต้องทำอะไรเพิ่ม ปล่อยให้โค้ดด้านล่างจัดการ
            // การเชื่อมต่อขาด
        }
        //> ออกจากลูปแล้ว = ติดต่อ Server ไม่ได้อีก -> แจ้ง GameState ให้หน้าจอขึ้นสถานะ 'ขาดการเชื่อมต่อ'
        state.connectionLost();
    }

    //> send: ส่งคำสั่งไป Server เช่น send("START") / send("GUESS#ช้าง") ถูกเรียกจาก GameFrame
    public void send(String line) {
        //> เช็ก out != null กัน error ถ้ายังเชื่อมต่อไม่สำเร็จ
        if (out != null) {
            out.println(line);
        }
    }

    //> close: ตอนผู้เล่นกดออก
    public void close() {
        //> บอก Server ก่อนว่า LEAVE เพื่อให้เอาชื่อออกจากห้องเรียบร้อย
        send("LEAVE");
        try {
            //> แล้วปิด socket (ทำให้ Thread รับข้อมูลที่รออ่านอยู่จบการทำงานเอง)
            socket.close();
        } catch (IOException e) {
            // ไม่ต้องทำอะไร
        }
    }
}
