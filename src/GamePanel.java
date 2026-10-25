//> [ไฟล์นี้ทำอะไร] วาดหน้าจอเกมทั้งหมดด้วยโค้ด (ไม่มีรูปภาพ/ไฟล์ภาพเลย) ตามเกณฑ์ 'วาดด้วย paintComponent'
//> เป็น View: อ่านข้อมูลจาก GameState แล้ววาดตามนั้น ไม่รู้จักเครือข่าย ไม่คำนวณคะแนนเอง
//> ทุกเครื่องอ่านข้อมูลที่ Server ส่ง จึงเห็นหน้าจอเหมือนกัน
//>
//> ความรู้ก่อนอ่านโค้ดวาด:
//>  - Graphics g = 'พู่กัน' ที่ Swing ส่งให้ตอนวาด  g.setColor(สี) เลือกสี  แล้วสั่งวาดรูป
//>  - พิกัด (x, y): มุมซ้ายบน = (0, 0)  x เพิ่ม = ไปทางขวา  y เพิ่ม = ลงล่าง
//>  - fillXxx = วาดแบบ 'ทึบ' / drawXxx = วาดแค่ 'เส้นขอบ'
//>  - g.drawString(ข้อความ, x, y): y คือเส้นฐานบรรทัด (baseline) ของตัวอักษร ไม่ใช่ขอบบน
//>  - ลำดับการวาดมีผล: วาดทีหลังจะทับของที่วาดก่อน (พื้นหลัง -> การ์ด -> เนื้อหา)
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Dimension;
import java.util.ArrayList;
import javax.swing.JPanel;

/**
 * หน้าจอเกมทั้งหมดวาดด้วย paintComponent (กล่องสี่เหลี่ยม วงกลม ตัวอักษร ป้ายคะแนน)
 * ข้อมูลที่ใช้วาดอ่านจาก GameState ซึ่งมาจาก Server → ทุกเครื่องเห็นเหมือนกัน
 */
//> extends JPanel = เป็นแผ่นผ้าใบที่เรากำหนดวิธีวาดเองได้โดย override paintComponent
public class GamePanel extends JPanel {
    //> สี: new Color(แดง, เขียว, น้ำเงิน) ค่า 0-255 ประกาศเป็น constant ไว้ที่เดียว อยากเปลี่ยนธีมแก้แค่ตรงนี้
    // สี (R, G, B)
    private static final Color BG = new Color(18, 20, 28);
    private static final Color CARD = new Color(31, 34, 46);
    private static final Color CARD_DARK = new Color(21, 23, 32);
    private static final Color YELLOW = new Color(242, 183, 74);
    private static final Color CREAM = new Color(230, 225, 211);
    private static final Color TEXT = new Color(240, 240, 245);
    private static final Color MUTED = new Color(138, 143, 168);
    private static final Color GREEN = new Color(76, 209, 126);
    private static final Color RED = new Color(255, 90, 90);
    private static final Color BORDER = new Color(154, 160, 186);
    private static final Color DARK_TEXT = new Color(27, 29, 39);

    //> state = แหล่งข้อมูลที่ใช้วาด (ส่งมาจาก GameFrame)
    private GameState state;
    private String fontName;

    //> Constructor
    public GamePanel(GameState state) {
        this.state = state;
        //> เลือกฟอนต์ไทย
        this.fontName = pickThaiFont();
        //> ขนาดที่ต้องการของแผงนี้ 1000 x 620 พิกเซล (GameFrame.pack() ใช้คำนวณขนาดหน้าต่าง)
        setPreferredSize(new Dimension(1000, 620));
    }

    //> pickThaiFont: หาฟอนต์ที่รองรับภาษาไทย (ลอจิกเดียวกับใน GameFrame)
    /** เลือกฟอนต์ที่มีตัวอักษรไทยบนเครื่องนี้ */
    private String pickThaiFont() {
        String[] candidates = {"Leelawadee UI", "Tahoma", "Thonburi", "Noto Sans Thai", "Sarabun"};
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (int i = 0; i < candidates.length; i++) {
            for (int j = 0; j < available.length; j++) {
                if (available[j].equalsIgnoreCase(candidates[i])) {
                    return available[j];
                }
            }
        }
        return "Dialog";
    }

    //> font(): ทางลัดสร้างฟอนต์ ใช้ฟอนต์ไทยที่เลือกไว้ + กำหนดสไตล์ (ปกติ/ตัวหนา) กับขนาด
    private Font font(int style, int size) {
        return new Font(fontName, style, size);
    }

    //> ========= วาดหน้าจอ =========
    // ================= วาดหน้าจอ (ถูกเรียกทุกครั้งที่ repaint) =================

    //> paintComponent: Swing เรียกให้เองทุกครั้งที่ต้องวาด (เช่น Timer ใน GameFrame สั่ง repaint() ทุก 33 ms)
    //> ห้ามเรียกเมธอดนี้เองโดยตรง ให้เรียก repaint() แทน  ทุกครั้งที่ถูกเรียกจะ 'วาดใหม่ทั้งภาพ' จากข้อมูลล่าสุดใน state
    protected void paintComponent(Graphics g) {
        //> super.paintComponent: ล้างของเก่าบนผ้าใบก่อน
        super.paintComponent(g);

        //> ระบายพื้นหลังทั้งแผงด้วยสีเข้ม (getWidth/getHeight = ขนาดแผง)
        g.setColor(BG);
        g.fillRect(0, 0, getWidth(), getHeight());

        //> วาดส่วนหัว (ชื่อเกม + ป้ายรอบ)
        drawHeader(g);

        //> fillRoundRect(x, y, กว้าง, สูง, ความมนแนวนอน, ความมนแนวตั้ง) = สี่เหลี่ยมมุมมน (ทึบ)
        g.setColor(CARD);
        //> การ์ดซ้าย: พื้นที่เล่น  (x 30..670  ->  กึ่งกลาง x = 350 จึงใช้ centerX = 350 ทั้งไฟล์)
        g.fillRoundRect(30, 90, 640, 500, 28, 28);     // การ์ดซ้าย (พื้นที่เล่น)
        //> การ์ดขวา: แถบคะแนน (x 690..970)
        g.fillRoundRect(690, 90, 280, 500, 28, 28);    // การ์ดขวา (คะแนน)

        //> ดู phase จาก state ว่าจะวาดหน้าไหน (Server เป็นคนกำหนด phase)
        String phase = state.getPhase();
        //> กำลังเล่น
        if (phase.equals("PLAYING")) {
            drawPlaying(g);
        } else if (phase.equals("GAMEOVER")) {
            //> จบเกม
            drawGameOver(g);
        } else {
            //> นอกนั้น (LOBBY/CONNECTING) = ห้องรอ
            drawLobby(g);
        }
        //> แถบคะแนนวาดทุกหน้า
        drawScoreboard(g);
    }

    //> drawHeader: วาดชื่อเกมมุมซ้ายบน
    private void drawHeader(Graphics g) {
        //> ตั้งฟอนต์ตัวหนา 36
        g.setFont(font(Font.BOLD, 36));
        //> ตั้งสี (เหลือง) ให้คำสั่งวาดถัดไป
        g.setColor(YELLOW);
        //> drawString(ข้อความ, x=40, y=64)
        g.drawString("WORD DASH", 40, 64);
        //> วัดความกว้างข้อความ "WORD DASH" (FontMetrics) เพื่อเอาไปวางข้อความถัดไปให้ต่อกัน
        int titleWidth = g.getFontMetrics().stringWidth("WORD DASH");

        g.setFont(font(Font.PLAIN, 14));
        g.setColor(MUTED);
        //> คำอธิบายเล็กๆ ต่อท้ายชื่อเกม (x = 40 + ความกว้างชื่อ + 12)
        g.drawString("เกมแข่งทายคำ", 40 + titleWidth + 12, 64);

        //> ป้าย 'รอบที่ x / y' แสดงเฉพาะตอนเล่น
        if (state.getPhase().equals("PLAYING") && state.getTotalRounds() > 0) {
            //> ข้อความรอบ เช่น "รอบที่ 3 / 10"
            String text = "รอบที่ " + state.getRound() + " / " + state.getTotalRounds();
            g.setFont(font(Font.BOLD, 15));
            //> ความกว้างป้าย = ความกว้างข้อความ + ขอบ 40 พิกเซล (ป้ายจึงยืดตามข้อความ)
            int w = g.getFontMetrics().stringWidth(text) + 40;
            //> x = 800 - w : จัดป้ายให้ขอบขวาอยู่ที่ x = 800
            int x = 800 - w;
            g.setColor(CARD);
            //> วาดแคปซูลพื้นป้าย (ความมน 38 = เกือบครึ่งวงกลม)
            g.fillRoundRect(x, 34, w, 38, 38, 38);
            g.setColor(TEXT);
            //> วาดข้อความในป้าย (ขยับเข้ามา 20 พิกเซลจากขอบซ้าย)
            g.drawString(text, x + 20, 59);
        }
    }

    // ---------- หน้ากำลังเล่น ----------
    //> drawPlaying: หน้าที่กำลังเล่น: เวลา / คำใบ้ / ช่องตัวอักษร / ข้อความล่าง
    private void drawPlaying(Graphics g) {
        //> over = รอบนี้จบแล้ว กำลังเฉลย
        boolean over = state.isRoundOver();
        //> centerX = กึ่งกลางการ์ดซ้าย
        int centerX = 350;

        //> -- เวลานับถอยหลัง --
        // เวลานับถอยหลัง
        //> secs = วินาทีที่เหลือ (ค่าที่ Server ส่ง TICK มา)
        int secs = state.getRemaining();
        //> แปลงเป็น mm:ss เช่น 75 วินาที -> "01:15" (secs / 60 = นาที, secs % 60 = เศษวินาที)
        String time = twoDigits(secs / 60) + ":" + twoDigits(secs % 60);
        g.setFont(font(Font.BOLD, 64));
        g.setColor(TEXT);
        //> วาดเวลากึ่งกลาง (drawCentered อยู่ด้านล่างไฟล์)
        drawCentered(g, time, centerX, 215);

        // คำใบ้
        //> -- คำใบ้ --  ป้ายเล็ก "คำใบ้" 
        g.setFont(font(Font.PLAIN, 13));
        g.setColor(MUTED);
        drawCentered(g, "คำใบ้", centerX, 302);

        //> ตัวคำใบ้ ฟอนต์ 21
        g.setFont(font(Font.PLAIN, 21));
        g.setColor(TEXT);
        //> wrap = ตัดคำใบ้ที่ยาวเป็นหลายบรรทัด ไม่ให้กว้างเกิน 580 พิกเซล (g.getFontMetrics() ใช้วัดความกว้าง)
        ArrayList<String> lines = wrap(g.getFontMetrics(), state.getHint(), 580);
        //> y = ตำแหน่งบรรทัดแรก
        int y = 332;
        //> วาดทีละบรรทัด
        for (int i = 0; i < lines.size(); i++) {
            drawCentered(g, lines.get(i), centerX, y);
            //> ลงบรรทัดถัดไป 30 พิกเซล
            y = y + 30;
        }

        //> -- กล่องตัวอักษร (ส่วนสำคัญ) --
        drawLetterBoxes(g, centerX, over);

        // ข้อความสถานะด้านล่าง
        //> -- ข้อความด้านล่าง --
        g.setFont(font(Font.PLAIN, 14));
        //> รอบจบ: โชว์เฉลยสีเหลือง / ระหว่างเล่น: โชว์ข้อความล่าสุดจากระบบ (ใครตอบถูก/ผิด/เปิดตัวอักษรช่วย)
        if (over) {
            g.setColor(YELLOW);
            drawCentered(g, "เฉลย: " + state.getAnswer(), centerX, 560);
        } else {
            g.setColor(MUTED);
            drawCentered(g, state.getLastMsg(), centerX, 560);
        }
    }

    //> drawLetterBoxes: วาดกล่องตัวอักษร 1 กล่องต่อ 1 ช่อง
    //> ช่องที่ยังปิด = กรอบเปล่า / ช่องที่เปิดแล้ว = พื้นครีมมีตัวอักษร / ตอนเฉลย = พื้นเหลืองทุกช่อง
    /** วาดกล่องตัวอักษร: ช่องที่เปิดแล้วเป็นสีครีม ช่องที่ยังปิดเป็นกรอบเปล่า */
    private void drawLetterBoxes(Graphics g, int centerX, boolean over) {
        //> pattern จาก Server เช่น ["_", "า", "_"]
        ArrayList<String> pattern = state.getPattern();
        //> n = จำนวนช่อง
        int n = pattern.size();
        //> ยังไม่มีข้อมูล (เช่นห้องรอ) -> ไม่วาดอะไร
        if (n == 0) {
            return;
        }
        //> gap = ช่องไฟระหว่างกล่อง
        int gap = 10;
        //> คำนวณขนาดกล่องให้พอดีกับพื้นที่ 580 พิกเซล: (พื้นที่ - ช่องไฟทั้งหมด) / จำนวนช่อง
        //> คำยาวกล่องจะเล็กลงเอง
        int size = (580 - gap * (n - 1)) / n;
        //> แต่ไม่ใหญ่เกิน 60
        if (size > 60) {
            size = 60;
        }
        //> ความกว้างรวมทุกกล่อง = กล่อง n อัน + ช่องไฟ (n-1) ช่อง
        int totalWidth = n * size + (n - 1) * gap;
        //> x เริ่มต้น = กึ่งกลาง - ครึ่งหนึ่งของความกว้างรวม  -> จัดกลางพอดี
        int startX = centerX - totalWidth / 2;
        //> y ของแถวกล่อง
        int boxY = 430;

        //> เตรียมคำเฉลย: แยกเป็นช่องด้วย WordEntry.slots() (ตัวเดียวกับที่ Server ใช้ จำนวนช่องจึงตรงกันแน่นอน)
        //> (ช่วงเล่นคำตอบเป็น "" จึงได้ลิสต์ว่าง ไม่ได้ใช้)
        // ตอนเฉลย ให้แสดงคำตอบจริงทุกช่อง
        ArrayList<String> answerSlots = new WordEntry(state.getAnswer(), "").slots();

        //> วนวาดทีละกล่อง
        for (int i = 0; i < n; i++) {
            //> boxX = ตำแหน่ง x ของกล่องที่ i (ขยับทีละ ขนาดกล่อง + ช่องไฟ)
            int boxX = startX + i * (size + gap);
            //> letter = ตัวอักษรของช่องนี้ ("_" = ยังปิด)
            String letter = pattern.get(i);
            //> shown = เปิดแล้วหรือยัง (ไม่ใช่ "_")
            boolean shown = !letter.equals("_");
            //> ตอนเฉลย: ใช้ตัวอักษรจากคำเฉลยจริงแทน และถือว่าเปิดหมด
            if (over && i < answerSlots.size()) {
                letter = answerSlots.get(i);
                shown = true;
            }

            //> ตอนเฉลย: พื้นเหลืองทึบ
            if (over) {
                g.setColor(YELLOW);
                g.fillRoundRect(boxX, boxY, size, size, 12, 12);
            } else if (shown) {
                //> ช่องที่เปิดแล้ว: พื้นครีมทึบ
                g.setColor(CREAM);
                g.fillRoundRect(boxX, boxY, size, size, 12, 12);
            } else {
                //> ช่องที่ยังปิด: วาดแค่กรอบ
                g.setColor(BORDER);
                //> drawRoundRect = วาดเส้นขอบอย่างเดียว (ไม่ทึบ)
                g.drawRoundRect(boxX, boxY, size, size, 12, 12);
                //> วาดซ้อนอีกอันเล็กกว่า 2 พิกเซล เพื่อให้เส้นหนาขึ้น
                g.drawRoundRect(boxX + 1, boxY + 1, size - 2, size - 2, 12, 12);   // วาดซ้อนให้เส้นหนาขึ้น
            }

            //> ถ้าต้องโชว์ตัวอักษร (เปิดแล้ว/เฉลย)
            if (shown) {
                //> ขนาดตัวอักษร = ครึ่งหนึ่งของกล่อง
                g.setFont(font(Font.BOLD, size / 2));
                g.setColor(DARK_TEXT);
                //> วางตัวอักษรกลางกล่อง: y = กลางกล่อง + size/6 (ชดเชยให้ตัวอักษรดูอยู่กลางจริง เพราะ y คือเส้นฐาน)
                drawCentered(g, letter, boxX + size / 2, boxY + size / 2 + size / 6);
            }
        }
    }

    // ---------- หน้าห้องรอ ----------
    //> drawLobby: หน้าห้องรอ
    private void drawLobby(Graphics g) {
        int centerX = 350;
        g.setFont(font(Font.BOLD, 30));
        g.setColor(YELLOW);
        drawCentered(g, "ห้องรอผู้เล่น", centerX, 220);

        //> count = จำนวนผู้เล่นในห้องตอนนี้
        int count = state.getPlayers().size();
        g.setFont(font(Font.PLAIN, 20));
        g.setColor(TEXT);
        //> "ผู้เล่นในห้อง 2 / 8 คน"
        drawCentered(g, "ผู้เล่นในห้อง " + count + " / " + Protocol.MAX_PLAYERS + " คน", centerX, 280);

        g.setFont(font(Font.PLAIN, 16));
        g.setColor(MUTED);
        //> บอกขั้นต่ำที่ต้องมี (ค่ามาจาก Server)
        drawCentered(g, "ต้องมีผู้เล่นอย่างน้อย " + state.getMinPlayers() + " คนจึงจะเริ่มเกมได้", centerX, 315);

        g.setFont(font(Font.PLAIN, 18));
        //> ข้อความสถานะตามบทบาท: หัวหน้าห้อง = ข้อความสีเขียวบอกให้กดเริ่ม / คนอื่น = รอหัวหน้า / ไม่ได้เชื่อมต่อ = ข้อความสีแดง (เช่น ห้องเต็ม)
        boolean iAmHost = state.getMyName().equals(state.getHost());
        if (!state.isConnected()) {
            g.setColor(RED);
            drawCentered(g, state.getStatus(), centerX, 400);
        } else if (iAmHost) {
            g.setColor(GREEN);
            drawCentered(g, "คุณเป็นหัวหน้าห้อง กดปุ่ม \"เริ่มเกม\" ด้านล่างเมื่อทุกคนพร้อม", centerX, 400);
        } else {
            g.setColor(MUTED);
            drawCentered(g, "รอหัวหน้าห้อง (" + state.getHost() + ") กดเริ่มเกม...", centerX, 400);
        }

        //> แอนิเมชันจุดรอ 3 จุด: t เปลี่ยนค่าทุก 0.4 วินาที, dots = t mod 4 ได้ 0,1,2,3 วนไป
        //> (ใช้ได้ต่อเนื่องเพราะ Timer สั่งวาดใหม่ทุก 33 ms)
        // จุดรอ 3 จุด ที่ค่อยๆ เพิ่มขึ้น
        long t = System.currentTimeMillis() / 400;
        int dots = (int) (t % 4);
        g.setColor(YELLOW);
        //> วนจุด 3 จุด: จุดที่ index < dots = วงกลมทึบ (fillOval) ที่เหลือ = วงกลมเปล่า (drawOval)
        for (int i = 0; i < 3; i++) {
            if (i < dots) {
                g.fillOval(centerX - 30 + i * 24, 440, 12, 12);
            } else {
                g.drawOval(centerX - 30 + i * 24, 440, 12, 12);
            }
        }

        g.setFont(font(Font.PLAIN, 14));
        g.setColor(MUTED);
        drawCentered(g, state.getLastMsg(), centerX, 560);
    }

    // ---------- หน้าจบเกม ----------
    //> drawGameOver: หน้าจบเกม ชื่อผู้ชนะ (winner มาจากข้อความ GAMEOVER ของ Server)
    private void drawGameOver(Graphics g) {
        int centerX = 350;
        g.setFont(font(Font.BOLD, 34));
        g.setColor(YELLOW);
        drawCentered(g, "จบเกม!", centerX, 200);

        g.setFont(font(Font.PLAIN, 18));
        g.setColor(MUTED);
        drawCentered(g, "ผู้ชนะ", centerX, 270);

        g.setFont(font(Font.BOLD, 30));
        g.setColor(TEXT);
        drawCentered(g, state.getWinner(), centerX, 325);

        //> ถ้วยรางวัล: วาดจากรูปทรงง่ายๆ 3 ชิ้น (ตัวถ้วยมน + ก้าน + ฐาน) ไม่ใช้รูปภาพ
        // ถ้วยรางวัล วาดจากสี่เหลี่ยมมน + สี่เหลี่ยม
        g.setColor(YELLOW);
        g.fillRoundRect(centerX - 40, 370, 80, 70, 30, 30);
        g.fillRect(centerX - 8, 435, 16, 30);
        g.fillRoundRect(centerX - 32, 465, 64, 12, 6, 6);

        g.setFont(font(Font.PLAIN, 14));
        g.setColor(MUTED);
        drawCentered(g, "กำลังกลับสู่ห้องรอ...", centerX, 560);
    }

    //> drawScoreboard: แถบคะแนนด้านขวา วนวาดผู้เล่นทีละแถว (ลำดับเรียงจาก Server แล้ว)
    // ---------- แถบคะแนนด้านขวา ----------
    private void drawScoreboard(Graphics g) {
        g.setFont(font(Font.BOLD, 15));
        g.setColor(MUTED);
        g.drawString("คะแนนผู้เล่น", 710, 128);

        //> players = ผู้เล่นทุกคนจาก SCORES (ได้สำเนา ปลอดภัยต่อ Thread)
        ArrayList<PlayerInfo> players = state.getPlayers();
        //> y เริ่มต้นของแถวแรก
        int y = 145;
        for (int i = 0; i < players.size(); i++) {
            PlayerInfo p = players.get(i);
            //> first = อันดับ 1 และมีคะแนนมากกว่า 0 -> ไฮไลต์สีทอง
            boolean first = (i == 0 && p.getScore() > 0);       // อันดับ 1 เป็นสีทอง
            //> me = แถวนี้คือฉันหรือเปล่า
            boolean me = p.getName().equals(state.getMyName());

            //> สีพื้นแถว: ทอง / เข้ม
            if (first) {
                g.setColor(YELLOW);
            } else {
                g.setColor(CARD_DARK);
            }
            //> วาดพื้นแถว
            g.fillRoundRect(706, y, 248, 42, 12, 12);
            //> แถวของตัวเอง (ที่ไม่ใช่อันดับ 1): วาดกรอบเหลืองทับ
            if (me && !first) {
                g.setColor(YELLOW);
                g.drawRoundRect(706, y, 248, 42, 12, 12);        // กรอบเหลืองรอบชื่อตัวเอง
            }

            //> สีตัวอักษร: บนพื้นทองใช้สีเข้ม
            Color textColor = TEXT;
            if (first) {
                textColor = DARK_TEXT;
            }
            g.setFont(font(Font.BOLD, 15));
            g.setColor(textColor);
            //> ป้ายชื่อ: ต่อท้าย "(คุณ)" ถ้าเป็นเรา
            String label = p.getName();
            if (me) {
                label = label + " (คุณ)";
            }
            //> วาดชื่อชิดซ้ายของแถว
            g.drawString(label, 720, y + 27);

            //> "" + ตัวเลข = แปลงเป็น String
            String scoreText = "" + p.getScore();
            //> วัดความกว้างตัวเลขเพื่อจัดชิดขวา
            int scoreWidth = g.getFontMetrics().stringWidth(scoreText);
            //> x = 940 - ความกว้าง : ขอบขวาของตัวเลขอยู่ที่ x = 940 ไม่ว่าคะแนนจะกี่หลัก
            g.drawString(scoreText, 940 - scoreWidth, y + 27);

            //> จุดเขียวหน้าคะแนน = ตอบถูกแล้วในรอบนี้ (แสดงเฉพาะตอน PLAYING)
            // จุดแสดงว่าตอบถูกแล้วในรอบนี้
            if (p.isSolved() && state.getPhase().equals("PLAYING")) {
                if (first) {
                    g.setColor(DARK_TEXT);
                } else {
                    g.setColor(GREEN);
                }
                //> fillOval(x, y, กว้าง, สูง) = วงรี/วงกลมทึบ
                g.fillOval(946 - scoreWidth - 28, y + 15, 12, 12);
            }
            //> ขยับลงแถวถัดไป 50 พิกเซล
            y = y + 50;
        }

        //> ข้อความเล็กๆ ล่างขวา: จำนวนผู้เล่น และสถานะการเชื่อมต่อ
        g.setFont(font(Font.PLAIN, 12));
        g.setColor(MUTED);
        g.drawString("เชื่อมต่อผู้เล่น: " + players.size() + "/" + Protocol.MAX_PLAYERS, 710, 548);
        //> ค่าเริ่มต้น: ถ้าไม่ได้เชื่อมต่อให้ขึ้น 'ขาดการเชื่อมต่อ'
        String statusText = "ขาดการเชื่อมต่อ";
        //> ถ้าเชื่อมต่ออยู่ (หรือกำลังเชื่อมต่อ) ใช้ข้อความสถานะจริง (ตัดให้ไม่ล้นความกว้าง 200)
        if (state.isConnected() || state.getPhase().equals("CONNECTING")) {
            statusText = shorten(g.getFontMetrics(), state.getStatus(), 200);
        }
        g.drawString("สถานะ: " + statusText, 710, 570);
    }

    // ================= ตัวช่วยวาดข้อความ =================

    //> twoDigits: เติม 0 นำหน้าเลขหลักเดียว 5 -> "05"
    private String twoDigits(int n) {
        if (n < 10) {
            return "0" + n;
        }
        return "" + n;
    }

    //> drawCentered: เขียนข้อความให้ 'กึ่งกลางอยู่ที่ x = centerX' (drawString ปกติเริ่มที่ขอบซ้าย)
    /** วาดข้อความให้อยู่กึ่งกลางที่ตำแหน่ง centerX */
    private void drawCentered(Graphics g, String text, int centerX, int baseline) {
        //> x เริ่มต้น = กึ่งกลาง - ครึ่งหนึ่งของความกว้างข้อความ
        int width = g.getFontMetrics().stringWidth(text);
        g.drawString(text, centerX - width / 2, baseline);
    }

    //> shorten: ตัดข้อความที่ยาวเกินความกว้าง maxWidth ทีละตัวอักษรจนพอดี
    //> (หมายเหตุ: คำอธิบายเดิมบอกว่าต่อท้าย ... แต่โค้ดจริงตัดเฉยๆ ไม่ได้เติม ...)
    /** ตัดข้อความที่ยาวเกินพื้นที่ให้สั้นลงแล้วต่อท้ายด้วย ... */
    private String shorten(FontMetrics fm, String text, int maxWidth) {
        //> วนตัดตัวอักษรท้ายออกทีละตัว ตราบที่ยังกว้างเกิน
        while (text.length() > 1 && fm.stringWidth(text) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    //> wrap: ตัดข้อความยาวให้เป็นหลายบรรทัดตามช่องว่าง (คำใบ้ทุกข้อมีช่องว่างคั่นวลี)
    /** ตัดบรรทัดข้อความตามช่องว่าง (คำใบ้ทุกข้อมีช่องว่างคั่นวลี) */
    private ArrayList<String> wrap(FontMetrics fm, String text, int maxWidth) {
        ArrayList<String> lines = new ArrayList<>();
        //> แยกข้อความเป็นคำๆ ด้วยช่องว่าง
        String[] words = text.split(" ");
        //> current = บรรทัดที่กำลังต่อ
        String current = "";
        //> วนทีละคำ
        for (int i = 0; i < words.length; i++) {
            //> test = ลองต่อคำนี้เข้าบรรทัดปัจจุบัน
            String test = words[i];
            if (!current.equals("")) {
                test = current + " " + words[i];
            }
            //> ถ้าต่อแล้วกว้างเกิน (และบรรทัดไม่ว่าง) -> ปิดบรรทัดเดิมเก็บไว้ แล้วเริ่มบรรทัดใหม่ด้วยคำนี้
            //> ไม่งั้น -> ต่อคำเข้าบรรทัดเดิม
            if (fm.stringWidth(test) > maxWidth && !current.equals("")) {
                lines.add(current);         // บรรทัดเต็มแล้ว ขึ้นบรรทัดใหม่
                current = words[i];
            } else {
                current = test;
            }
        }
        //> อย่าลืมบรรทัดสุดท้ายที่ยังค้างอยู่
        if (!current.equals("")) {
            lines.add(current);
        }
        return lines;
    }
}
