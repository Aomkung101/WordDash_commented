//> [ไฟล์นี้ทำอะไร] ภาษากลางที่ Server กับ Client ใช้คุยกัน
//> ข้อความทุกอย่างเป็น String 1 บรรทัด รูปแบบ  คำสั่ง#ข้อมูล1#ข้อมูล2
//> ใช้ทั้งฝั่ง Server (ClientHandler, GameRoom) และฝั่ง Client (GameState, GameFrame, NetworkClient, GamePanel)
//> ไม่เก็บสถานะอะไรเลย ทุกอย่างเป็น static จึงไม่ต้อง new Protocol()
/**
 * ค่าคงที่และเมธอดช่วยที่ Server กับ Client ใช้ร่วมกัน
 * ข้อความที่ส่งหากันเป็น String 1 บรรทัด รูปแบบ  คำสั่ง#ข้อมูล1#ข้อมูล2...
 * เช่น  "TICK#12"  หรือ  "ROUND#3#10#คำใบ้#30#ค,_,า"
 */
//> static = เป็นของ 'คลาส' ไม่ใช่ของ object เรียกใช้ได้เลยด้วย Protocol.SEP หรือ Protocol.getCommand(...)
public class Protocol {
    //> final = ค่าคงที่ เปลี่ยนค่าไม่ได้
    //> ตัวคั่นคือ # ดังนั้นชื่อผู้เล่นห้ามมี # (GameRoom.cleanName ตัดออกให้)
    public static final String SEP = "#";      // ตัวคั่นข้อความ
    //> จำนวนผู้เล่นสูงสุด ใช้ทั้งฝั่ง Server (ปฏิเสธเมื่อห้องเต็ม) และฝั่ง Client (วาดคำว่า x / 8)
    public static final int MAX_PLAYERS = 8;   // จำนวนผู้เล่นสูงสุดในห้อง

    /** ดึงชื่อคำสั่ง (ข้อความก่อนตัวคั่นตัวแรก) เช่น "TICK#12" -> "TICK" */
    //> ตัวอย่าง: line = "ROUND#3#10#คำใบ้#30#ค,_,า"  ->  คืนค่า "ROUND"
    public static String getCommand(String line) {
        //> indexOf(SEP) = หาตำแหน่งของ # ตัวแรก (นับจาก 0) ถ้าไม่มีเลยจะได้ -1
        int pos = line.indexOf(SEP);
        if (pos < 0) {
            //> ไม่มี # = ข้อความมีแค่คำสั่งล้วนๆ เช่น "START" หรือ "LEAVE" จึงคืนทั้งบรรทัด
            return line;
        }
        //> substring(0, pos) = ตัดข้อความตั้งแต่ตัวที่ 0 ถึงก่อนตำแหน่ง pos (ไม่รวมตัว #)
        return line.substring(0, pos);
    }

    /** ดึงข้อมูลที่ตามหลังคำสั่ง เช่น "TICK#12" -> "12" */
    //> ตัวอย่าง: "TICK#12" -> "12"   /   "ROUND#3#10#..." -> "3#10#..." (เอาทุกอย่างหลัง # ตัวแรก)
    public static String getArgument(String line) {
        //> หาตำแหน่ง # ตัวแรกเหมือนเมธอดบน
        int pos = line.indexOf(SEP);
        if (pos < 0) {
            //> ไม่มีข้อมูลแนบ -> คืน String ว่าง "" (ไม่คืน null เพื่อกัน NullPointerException ตอนเอาไปใช้ต่อ)
            return "";
        }
        //> pos + 1 = ข้ามตัว # แล้วเอาทุกอย่างที่เหลือ
        //> ถ้าในข้อมูลมี # ซ้อนอยู่ ก็ยังได้ครบ GameState จะ split("#") ต่ออีกที
        return line.substring(pos + 1);
    }
}
