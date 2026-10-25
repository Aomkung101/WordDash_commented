//> [ไฟล์นี้ทำอะไร] จุดเริ่มต้นฝั่ง Client (มี main) รันด้วย: java GameFrame [ip] [port] [ชื่อ]
//> เป็น 'หน้าต่างหลัก' ของผู้เล่น: ประกอบหน้าจอ (GamePanel + ช่องพิมพ์ + ปุ่ม) และผูกทุกอย่างเข้าด้วยกัน
//>   GamePanel     = วาดหน้าจอเกม (อ่านข้อมูลจาก GameState)
//>   GameState     = กระดานสถานะ (อัปเดตโดย NetworkClient)
//>   NetworkClient = ต่อ Server และรับ/ส่งข้อความ
//>   Timer         = สั่งให้วาดหน้าจอใหม่ทุก 33 ms
//> import = ขอใช้คลาสสำเร็จรูปของ Java (Swing = ชุดสร้างหน้าต่าง, java.awt = สี/ฟอนต์/layout)
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;

/**
 * หน้าต่างหลักของผู้เล่น (Client)   รัน:  java GameFrame [ip] [port] [ชื่อ]
 *  - GamePanel      : วาดหน้าจอทั้งหมดด้วย paintComponent
 *  - javax.swing.Timer : สั่ง repaint ทุก 33 มิลลิวินาที เพื่ออัปเดตหน้าจอต่อเนื่อง
 *  - NetworkClient  : Thread แยกสำหรับรับข้อมูลจาก Server
 * คลาสนี้ implements ActionListener เพื่อรับเหตุการณ์จากปุ่ม ช่องพิมพ์ และ Timer
 */
//> extends JFrame = คลาสนี้ 'คือ' หน้าต่าง (สืบทอดความสามารถของ JFrame)
//> implements ActionListener = รับ 'เหตุการณ์' ได้ (กดปุ่ม, กด Enter, Timer ครบรอบ) ผ่านเมธอด actionPerformed ด้านล่าง
public class GameFrame extends JFrame implements ActionListener {
    //> 3 ตัวหลักที่ประกอบกัน: state (ข้อมูล) / panel (วาด) / net (เครือข่าย)
    private GameState state;
    private GamePanel panel;
    private NetworkClient net;
    //> answerField = ช่องพิมพ์คำตอบ
    private JTextField answerField;
    //> ปุ่ม: ตอบ / เริ่มเกม (เห็นเฉพาะหัวหน้าห้อง) / ออกจากเกม
    private JButton answerBtn;
    private JButton startBtn;
    private JButton exitBtn;
    //> timer = ตัวจับเวลาของ Swing (javax.swing.Timer) ทำให้เกิดเหตุการณ์ซ้ำๆ
    private Timer timer;
    //> wasPlaying = เก็บว่า 'รอบที่แล้ว' อยู่ในโหมดเล่นไหม เพื่อรู้ว่าเพิ่งเริ่มรอบใหม่ (ดู refreshControls)
    private boolean wasPlaying = false;
    //> fontName = ฟอนต์ที่มีภาษาไทย
    private String fontName;

    //> Constructor: สร้างหน้าต่างและเชื่อมต่อ Server
    //> throws IOException = ถ้าต่อ Server ไม่ได้จะโยน error ออกไปให้ main จัดการ
    public GameFrame(String host, int port, String name) throws IOException {
        //> super(...) = เรียก constructor ของ JFrame ตั้งชื่อหัวหน้าต่าง
        super("WORD DASH - เกมแข่งทายคำ");
        //> เลือกฟอนต์ภาษาไทยที่เครื่องนี้มี
        fontName = thaiFont();
        //> สร้างกระดานสถานะ (ว่างเปล่า) ก่อน
        state = new GameState();
        //> สร้างตัววาดหน้าจอ โดยส่ง state เข้าไป (GamePanel จะอ่านข้อมูลจาก state เท่านั้น)
        panel = new GamePanel(state);
        //> สร้างตัวเชื่อมต่อ Server โดยส่ง state เดียวกันเข้าไป (NetworkClient จะเขียนข้อมูลลง state)
        //> ==> panel กับ net ใช้ state ตัวเดียวกัน นี่คือหัวใจของการแยก Model / View / Network
        net = new NetworkClient(state);

        // ช่องพิมพ์คำตอบ + ปุ่มต่างๆ (แถบด้านล่าง)
        //> ตกแต่งช่องพิมพ์ (ฟอนต์ ขนาด สีพื้น สีตัวอักษร สีเคอร์เซอร์) ใช้ new Color(R, G, B) ค่า 0-255
        answerField = new JTextField();
        answerField.setFont(new Font(fontName, Font.PLAIN, 18));
        answerField.setBackground(new Color(21, 23, 32));
        answerField.setForeground(Color.WHITE);
        answerField.setCaretColor(Color.WHITE);
        //> สร้างปุ่มด้วยเมธอดช่วย makeButton(ข้อความ, สีพื้น, สีตัวอักษร)
        answerBtn = makeButton("ตอบ", new Color(242, 183, 74), Color.BLACK);
        startBtn = makeButton("เริ่มเกม", new Color(76, 209, 126), Color.BLACK);
        exitBtn = makeButton("ออกจากเกม", new Color(44, 48, 64), Color.WHITE);

        //> buttons = แผงเก็บปุ่ม จัดเรียงชิดขวา (FlowLayout.RIGHT) ช่องว่างระหว่างปุ่ม 8 พิกเซล
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        //> เพิ่มปุ่มเข้าแผง ตามลำดับซ้ายไปขวา
        buttons.add(answerBtn);
        buttons.add(startBtn);
        buttons.add(exitBtn);

        //> south = แผงด้านล่างของหน้าต่าง ใช้ BorderLayout (แบ่งเป็น ซ้าย/กลาง/ขวา/บน/ล่าง)
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.setBackground(new Color(18, 20, 28));
        south.setBorder(BorderFactory.createEmptyBorder(8, 40, 14, 40));
        //> ช่องพิมพ์อยู่ตรงกลาง (ขยายเต็มพื้นที่ที่เหลือ) ปุ่มอยู่ทางขวา (EAST)
        south.add(answerField, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.EAST);

        //> ทั้งหน้าต่างก็ใช้ BorderLayout เช่นกัน
        setLayout(new BorderLayout());
        //> GamePanel (หน้าจอเกม) อยู่ตรงกลางของหน้าต่าง
        add(panel, BorderLayout.CENTER);
        //> แผงล่าง (ช่องพิมพ์+ปุ่ม) อยู่ด้านล่าง
        add(south, BorderLayout.SOUTH);

        // ผูกเหตุการณ์ทั้งหมดเข้ากับ actionPerformed ของคลาสนี้
        //> addActionListener(this) = บอกปุ่มว่า 'เมื่อถูกกด ให้เรียก actionPerformed ของคลาสนี้' (this = object นี้)
        answerBtn.addActionListener(this);
        answerField.addActionListener(this);     // กด Enter ในช่องพิมพ์
        startBtn.addActionListener(this);
        exitBtn.addActionListener(this);

        //> ปิดหน้าต่าง (กากบาท) = จบโปรแกรมทั้งหมด
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        //> pack() = ปรับขนาดหน้าต่างให้พอดีกับของข้างใน (GamePanel กำหนดไว้ 1000x620)
        pack();
        //> ห้ามผู้ใช้ลากขยายหน้าต่าง (เพราะวาดด้วยพิกัดตายตัว)
        setResizable(false);
        //> วางหน้าต่างกลางจอ (null = กลางจอ)
        setLocationRelativeTo(null);

        //> เชื่อมต่อ Server จริง: ต่อ Socket, ส่ง JOIN#ชื่อ, เริ่ม Thread รับข้อมูล
        //> ถ้า Server ไม่เปิด/IP ผิด จะโยน IOException ออกจาก constructor -> main จับแล้วขึ้นกล่อง error
        net.connect(host, port, name);

        //> สร้าง Timer: ทุก 33 มิลลิวินาที (~30 ครั้ง/วินาที) ให้เรียก actionPerformed ของคลาสนี้
        //> Timer ของ Swing ทำงานบน Thread หน้าจอ (EDT) ซึ่งปลอดภัยในการสั่งแก้ไข/วาดหน้าจอ
        timer = new Timer(33, this);             // ทุก 33 มิลลิวินาที (~30 ครั้งต่อวินาที)
        //> เริ่มจับเวลา
        timer.start();
    }

    //> makeButton: เมธอดช่วยสร้างปุ่มที่หน้าตาเหมือนกันทุกปุ่ม (สีพื้น สีตัวอักษร ไม่มีกรอบ ขนาด 110x36)
    private JButton makeButton(String text, Color background, Color foreground) {
        JButton b = new JButton(text);
        b.setBackground(background);
        b.setForeground(foreground);
        //> setOpaque(true) + setBackground = บังคับให้ปุ่มทึบและโชว์สีที่ตั้ง (ไม่งั้นบางธีมของ Windows ไม่แสดงสี)
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setFont(new Font(fontName, Font.BOLD, 15));
        b.setPreferredSize(new Dimension(110, 36));
        return b;
    }

    /** เมธอดนี้ถูกเรียกทุกครั้งที่ปุ่มถูกกด / กด Enter / Timer ครบรอบ */
    //> actionPerformed: ศูนย์รวมเหตุการณ์ทั้งหมดของหน้าต่างนี้ ถูกเรียกเมื่อ: กดปุ่ม / กด Enter ในช่องพิมพ์ / Timer ครบ 33 ms
    //> e.getSource() บอกว่าเหตุการณ์มาจากอะไร แล้ว if-else แยกทำตามต้นทาง
    public void actionPerformed(ActionEvent e) {
        //> source = ตัวที่ทำให้เกิดเหตุการณ์
        Object source = e.getSource();
        if (source == timer) {
            //> Timer ครบรอบ: สั่งวาดหน้าจอใหม่ (repaint -> เรียก paintComponent) + อัปเดตสถานะปุ่ม
            //> เหตุผลที่ต้องมี: Thread รับข้อมูลเครือข่ายแก้แค่ GameState ไม่แตะหน้าจอ
            //> จึงต้องมี Timer มาคอยวาดจากข้อมูลล่าสุดเป็นประจำ ทำให้เวลา/คะแนนอัปเดตและแอนิเมชันจุดรอทำงานต่อเนื่อง
            panel.repaint();
            refreshControls();
        } else if (source == answerBtn || source == answerField) {
            //> กดปุ่ม 'ตอบ' หรือกด Enter ในช่องพิมพ์ = ส่งคำตอบ (ทั้งสองมาที่เมธอดเดียวกัน)
            submitAnswer();
        } else if (source == startBtn) {
            //> กด 'เริ่มเกม' = ส่งคำสั่ง START ให้ Server (Server จะเช็กเองว่าเป็นหัวหน้าและคนครบไหม)
            net.send("START");
        } else if (source == exitBtn) {
            //> กด 'ออกจากเกม' = บอก Server (LEAVE) + ปิดการเชื่อมต่อ แล้วจบโปรแกรม (System.exit(0))
            net.close();
            System.exit(0);
        }
    }

    //> submitAnswer: ส่งคำตอบที่พิมพ์ไป Server
    private void submitAnswer() {
        //> อ่านข้อความในช่อง + trim() ตัดช่องว่างหัวท้าย
        String text = answerField.getText().trim();
        //> ส่งเฉพาะกรณี: ไม่ใช่ข้อความว่าง และอยู่ในช่วงเล่น (PLAYING) เท่านั้น
        if (!text.equals("") && state.getPhase().equals("PLAYING")) {
            //> ส่ง GUESS#คำตอบ  (Protocol.SEP = "#") Client ไม่ตรวจเองว่าถูกไหม ปล่อยให้ Server ตัดสิน
            net.send("GUESS" + Protocol.SEP + text);
        }
        //> ล้างช่องพิมพ์ ให้พิมพ์คำถัดไปได้เลย
        answerField.setText("");
        //> ดึงเคอร์เซอร์กลับเข้าช่องพิมพ์ (เผื่อเพิ่งกดปุ่มตอบด้วยเมาส์)
        answerField.requestFocusInWindow();
    }

    //> refreshControls: เปิด/ปิดช่องพิมพ์และปุ่มตามสถานะเกม (ถูกเรียกทุก 33 ms จาก Timer)
    /** เปิด/ปิดช่องพิมพ์และปุ่มตามสถานะเกม */
    private void refreshControls() {
        //> playing = อยู่ในหน้าเล่นเกม และรอบนี้ยังไม่จบ (ช่วงโชว์เฉลยห้ามพิมพ์)
        boolean playing = state.getPhase().equals("PLAYING") && !state.isRoundOver();
        //> lobby = อยู่ห้องรอ
        boolean lobby = state.getPhase().equals("LOBBY");
        //> iAmHost = ชื่อฉันตรงกับชื่อหัวหน้าห้อง
        boolean iAmHost = state.getMyName().equals(state.getHost());

        //> ช่องพิมพ์ + ปุ่มตอบ ใช้ได้เฉพาะตอน playing
        answerField.setEnabled(playing);
        answerBtn.setEnabled(playing);
        //> ปุ่มเริ่มเกม โผล่เฉพาะ 'อยู่ห้องรอ และเป็นหัวหน้า'
        startBtn.setVisible(lobby && iAmHost);

        //> เคอร์เซอร์ลงช่องพิมพ์ 'ตอนเริ่มรอบใหม่ครั้งเดียว' (playing เพิ่งเปลี่ยนจาก false เป็น true)
        //> ถ้าสั่งทุก 33 ms จะแย่งโฟกัสตลอดเวลา
        if (playing && !wasPlaying) {
            answerField.requestFocusInWindow();  // เริ่มรอบใหม่ ให้เคอร์เซอร์อยู่ในช่องพิมพ์
        }
        //> จำสถานะไว้เทียบในรอบถัดไป
        wasPlaying = playing;
    }

    //> thaiFont: เลือกฟอนต์ที่รองรับภาษาไทย เพราะฟอนต์เริ่มต้นของ Java บางเครื่องแสดงไทยเป็นสี่เหลี่ยม
    /** เลือกฟอนต์ที่มีตัวอักษรไทยบนเครื่องนี้ (ฟอนต์เริ่มต้นของ Swing บางเครื่องไม่มีภาษาไทย) */
    private static String thaiFont() {
        //> รายชื่อฟอนต์ที่ต้องการ เรียงตามความชอบ (Leelawadee UI / Tahoma มีใน Windows, Thonburi ใน Mac)
        String[] candidates = {"Leelawadee UI", "Tahoma", "Thonburi", "Noto Sans Thai", "Sarabun"};
        //> ขอรายชื่อฟอนต์ทั้งหมดที่ติดตั้งในเครื่อง
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        //> วนหาว่าฟอนต์ที่ต้องการอันไหนมีในเครื่อง (ตัวแรกที่เจอจะถูกใช้) เทียบชื่อแบบไม่สนตัวพิมพ์เล็กใหญ่
        for (int i = 0; i < candidates.length; i++) {
            for (int j = 0; j < available.length; j++) {
                if (available[j].equalsIgnoreCase(candidates[i])) {
                    return available[j];
                }
            }
        }
        //> ไม่เจอเลย -> ใช้ "Dialog" (ฟอนต์สำรองของ Java อาจแสดงไทยไม่ครบ)
        return "Dialog";
    }

    //> makeLabel: สร้างข้อความ (JLabel) ฟอนต์ไทยสำหรับกล่องโต้ตอบ
    private static JLabel makeLabel(String text, String fontName) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(fontName, Font.PLAIN, 14));
        return label;
    }

    //> main: จุดเริ่มต้นฝั่ง Client  run_client.bat [ip] [port] [ชื่อ] -> args[0..2]
    public static void main(String[] args) {
        String fontName = thaiFont();
        //> ค่าเริ่มต้นช่องกรอก: localhost / 5000 (ถ้าพิมพ์ต่อท้ายคำสั่ง จะเอาค่านั้นมาใส่ให้แทน)
        String defaultHost = "localhost";
        String defaultPort = "5000";
        //> ถ้ามีการระบุ args ให้เอาค่านั้นแทนค่าเริ่มต้น
        if (args.length > 0) {
            defaultHost = args[0];
        }
        if (args.length > 1) {
            defaultPort = args[1];
        }
        String defaultName = "";
        if (args.length > 2) {
            defaultName = args[2];
        }

        //> สร้างกล่องกรอกข้อมูล 3 ช่อง (IP, พอร์ต, ชื่อ)
        // หน้าต่างถามไอพี พอร์ต และชื่อ
        JTextField hostField = new JTextField(defaultHost);
        JTextField portField = new JTextField(defaultPort);
        JTextField nameField = new JTextField(defaultName);
        hostField.setFont(new Font(fontName, Font.PLAIN, 14));
        portField.setFont(new Font(fontName, Font.PLAIN, 14));
        nameField.setFont(new Font(fontName, Font.PLAIN, 14));

        //> form = แผงที่ใส่ข้อความ+ช่องกรอก เรียงลงมาแนวตั้ง (GridLayout 0 แถว 1 คอลัมน์ = แถวไม่จำกัด)
        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.add(makeLabel("IP ของ Server:", fontName));
        form.add(hostField);
        form.add(makeLabel("พอร์ต:", fontName));
        form.add(portField);
        form.add(makeLabel("ชื่อผู้เล่น:", fontName));
        form.add(nameField);

        //> showConfirmDialog = โชว์กล่องโต้ตอบพร้อมปุ่ม OK/Cancel โปรแกรมหยุดรอจนผู้ใช้กด
        //> คืนค่าเป็นปุ่มที่ผู้ใช้เลือก
        int choice = JOptionPane.showConfirmDialog(null, form, "WORD DASH - เข้าร่วมเกม",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        //> กด Cancel/ปิด -> จบโปรแกรม
        if (choice != JOptionPane.OK_OPTION) {
            System.exit(0);
        }

        //> อ่านชื่อที่กรอก
        String playerName = nameField.getText().trim();
        //> ไม่กรอกชื่อ -> ใช้ "ผู้เล่น" (ถ้าซ้ำ Server จะต่อเลขให้เอง)
        if (playerName.equals("")) {
            playerName = "ผู้เล่น";
        }
        //> try-catch: ถ้าเชื่อมต่อไม่ได้จะเกิด error แล้วไปที่ catch (โปรแกรมไม่พัง แต่ขึ้นข้อความ)
        try {
            //> แปลงพอร์ตจากข้อความเป็นตัวเลข (ถ้าพิมพ์ไม่ใช่เลข จะเกิด error -> ไป catch)
            int port = Integer.parseInt(portField.getText().trim());
            //> สร้างหน้าต่างเกม (ใน constructor จะเชื่อมต่อ Server ด้วย)
            GameFrame frame = new GameFrame(hostField.getText().trim(), port, playerName);
            //> แสดงหน้าต่าง
            frame.setVisible(true);
        } catch (Exception ex) {
            //> เชื่อมต่อ Server ไม่ได้ (IP ผิด / Server ยังไม่เปิด / ไฟร์วอลล์บล็อก / พอร์ตไม่ตรง)
            //> ex.getMessage() = ข้อความ error จาก Java เช่น Connection refused / timed out แสดงให้ผู้ใช้ดู
            JLabel message = makeLabel("เชื่อมต่อ Server ไม่ได้: " + ex.getMessage(), fontName);
            JOptionPane.showMessageDialog(null, message, "ผิดพลาด", JOptionPane.ERROR_MESSAGE);
            //> จบโปรแกรมด้วยรหัส 1 (= ผิดพลาด)
            System.exit(1);
        }
    }
}
