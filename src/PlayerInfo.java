//> [ไฟล์นี้ทำอะไร] ผู้เล่น 1 แถวในตารางคะแนน ฝั่ง Client
//> GameState แปลงข้อความ SCORES จาก Server เป็น PlayerInfo แล้ว GamePanel เอาไปวาดตารางคะแนน
//> ไม่มี setter = อ่านอย่างเดียว เพราะ Client ห้ามคำนวณคะแนนเอง (Server เป็นคนตัดสินเสมอ)
/** ข้อมูลผู้เล่น 1 แถวในตารางคะแนน (ฝั่ง Client) */
public class PlayerInfo {
    //> ข้อมูล 1 รายการใน SCORES เช่น "สมชาย:107:1" -> name = สมชาย, score = 107, solved = true
    private String name;
    private int score;
    private boolean solved;

    //> Constructor: GameState.parseScores() เป็นคนเรียก
    public PlayerInfo(String name, int score, boolean solved) {
        this.name = name;
        this.score = score;
        this.solved = solved;
    }

    //> Getter อ่านค่าอย่างเดียว GamePanel.drawScoreboard() เรียกใช้
    public String getName() { return name; }
    public int getScore() { return score; }
    public boolean isSolved() { return solved; }
}
