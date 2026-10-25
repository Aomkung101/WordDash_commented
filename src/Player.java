//> [ไฟล์นี้ทำอะไร] ข้อมูลผู้เล่น 1 คนฝั่ง Server: ชื่อ คะแนน และตอบถูกแล้วหรือยัง
//> ClientHandler แต่ละตัวถือ Player ของตัวเองไว้ (ClientHandler.player)
//> ฝั่ง Client ไม่มี Player จะได้ PlayerInfo (สำเนาไว้แสดงผลอย่างเดียว) แทน
/** ข้อมูลผู้เล่น 1 คน (ฝั่ง Server) */
public class Player {
    //> private = ซ่อนข้อมูล (Encapsulation) โค้ดส่วนอื่นแก้ตรงๆ ไม่ได้ ต้องผ่านเมธอด get/set/add ด้านล่าง
    //> ป้องกันไม่ให้ใครแก้คะแนนมั่วๆ จากข้างนอก
    private String name;
    //> คะแนนสะสมทั้งเกม
    private int score;
    //> solved ถูกรีเซ็ตเป็น false ทุกต้นรอบ (GameRoom.startRound) เพื่อกันตอบถูกซ้ำในรอบเดียว
    private boolean solved;   // true = ตอบถูกแล้วในรอบนี้

    //> Constructor: ถูกเรียกตอน new Player(ชื่อ) ใน GameRoom.join()
    public Player(String name) {
        //> this.name = ตัวแปรของ object นี้   ส่วน name ด้านขวา = พารามิเตอร์ที่ส่งเข้ามา
        this.name = name;
        this.score = 0;
        this.solved = false;
    }

    //> Getter = เมธอดสำหรับ 'อ่าน' ค่า
    //> (ไม่มี setName เพราะชื่อไม่ควรเปลี่ยนหลังเข้าห้อง)
    public String getName() { return name; }
    public int getScore() { return score; }
    public boolean isSolved() { return solved; }
    //> Setter: ตั้งเป็น true เมื่อตอบถูก / false ตอนเริ่มรอบใหม่
    public void setSolved(boolean solved) { this.solved = solved; }
    //> บวกคะแนนเพิ่ม (ไม่ใช่ตั้งค่าใหม่) เรียกจาก GameRoom.handleGuess()
    public void addScore(int points) { score = score + points; }
    //> เริ่มเกมใหม่ทุกครั้งต้องกลับเป็น 0 (เรียกใน GameRoom.start())
    public void resetScore() { score = 0; }
}
