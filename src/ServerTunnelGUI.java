import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class ServerTunnelGUI {
    private Random r = new Random();
    private Process pythonProcess;
    private Process tunnelProcess;
    private JTextArea serverOutput;
    private JTextArea tunnelOutput;
    private JScrollPane serverScroll;
    private JScrollPane tunnelScroll;
    private JButton copyButton;
    public JPanel loadPanel;
    public JPanel serverPanel;
    public JPanel tunnelPanel;
    public JPanel centerPanel;
    public JFrame frame;
    public String path, originalPath, link;

    // === 新增：mode 相關 ===
    public enum MODE { UI, CONSOLE }
    public MODE currentMode;

    // === 新增：UI_Mode 元件 ===
    private JTextField tunnelStateField;
    private JLabel totalVisitorLabel;
    private JTree fileTree;
    private JScrollPane fileTreeScroll;
    private JLabel fileVisitLabel;
    private JPanel uiServerCenter;
    private JPanel uiTunnelCenter;

    // === 新增：toolList 按鈕 ===
    private JPanel toolList;
    private JButton switchModeButton;


    // === 新增：檔案訪問次數記錄（簡單以路徑為key）===
    private java.util.Map<String, Integer> fileVisitMap = new java.util.HashMap<>();

    public ServerTunnelGUI() {
        path = System.getProperty("user.dir");
        try {
            originalPath = new File(
                    ServerTunnelGUI.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getParent();
        } catch (Exception e) {
            e.printStackTrace();
            originalPath = "";
        }
    }

    private boolean isPortAvailable(int port) {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private int getFreePort() {
        for (int i = 0; i < 50; i++) {
            int candidate = r.nextInt(1000) + 8000;
            if (isPortAvailable(candidate)) {
                return candidate;
            }
        }
        throw new RuntimeException("找不到可用的埠");
    }

    // === 新增：更新 tunnelStateField 顯示 ===
    private void updateTunnelStateField() {
        if (tunnelStateField == null) return;
        SwingUtilities.invokeLater(() -> {
            if (tunnelState == null) {
                tunnelStateField.setText("未啟動");
            } else switch (tunnelState) {
                case LOADING:
                    tunnelStateField.setText("連線中...");
                    tunnelStateField.setForeground(Color.ORANGE);
                    break;
                case SUCCESS:
                    tunnelStateField.setText("已連線: " + (link != null ? link : ""));
                    tunnelStateField.setForeground(new Color(0, 180, 0));
                    break;
                case net_ERROR:
                    tunnelStateField.setText("網路錯誤，無法連線至 Cloudflare");
                    tunnelStateField.setForeground(Color.RED);
                    break;
                case req_ERROR:
                	tunnelStateField.setText("tunnel建立的頻率過高，請稍後再試");
                	tunnelStateField.setForeground(Color.RED);
            }
        });
    }

    // === 新增：更新 totalVisitor 標籤 ===
    private void updateTotalVisitorLabel() {
        if (totalVisitorLabel == null) return;
        SwingUtilities.invokeLater(() -> totalVisitorLabel.setText("總訪客數: " + totalVisitor));
    }

    private javax.swing.tree.DefaultMutableTreeNode buildFileTreeNode(File file) {
        javax.swing.tree.DefaultMutableTreeNode node = new javax.swing.tree.DefaultMutableTreeNode(file.getName().isEmpty() ? file.getAbsolutePath() : file.getName()) {
            public File file = null;
        };
        node.setUserObject(file);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                java.util.Arrays.sort(children, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareTo(b.getName());
                });
                for (File child : children) {
                    node.add(buildFileTreeNode(child));
                }
            }
        }
        return node;
    }

    private JTree buildFileTree() {
        javax.swing.tree.DefaultMutableTreeNode root = buildFileTreeNode(new File(path));
        JTree tree = new JTree(root);
        tree.setCellRenderer(new javax.swing.tree.DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                if (value instanceof javax.swing.tree.DefaultMutableTreeNode) {
                    Object uo = ((javax.swing.tree.DefaultMutableTreeNode) value).getUserObject();
                    if (uo instanceof File) {
                        setText(((File) uo).getName().isEmpty() ? ((File) uo).getAbsolutePath() : ((File) uo).getName());
                    }
                }
                return this;
            }
        });
        tree.addTreeSelectionListener(e -> {
            javax.swing.tree.DefaultMutableTreeNode node =
                (javax.swing.tree.DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node == null || fileVisitLabel == null) return;
            Object uo = node.getUserObject();
            if (uo instanceof File) {
                File selected = (File) uo;
                String absPath = selected.getAbsolutePath();
                int count = fileVisitMap.getOrDefault(absPath, 0);
                fileVisitLabel.setText("「" + selected.getName() + "」被訪問次數: " + count);
            }
        });
        return tree;
    }
    private void startMode() {
        SwingUtilities.invokeLater(() -> {
            frame.remove(loadPanel);
            frame.add(centerPanel, BorderLayout.CENTER);
            frame.revalidate();
            frame.repaint();
        });
    }
    // === 新增：UI_Mode 實作 ===
    private void UI_Mode() {
    	startMode();
        currentMode = MODE.UI;

        // --- tunnelPanel ---
        tunnelPanel.removeAll();
        tunnelStateField = new JTextField("未啟動");
        tunnelStateField.setEditable(false);
        tunnelStateField.setHorizontalAlignment(JTextField.CENTER);
        tunnelStateField.setFont(new Font("Monospaced", Font.BOLD, 14));
        uiTunnelCenter = new JPanel(new BorderLayout());
        uiTunnelCenter.add(new JLabel("Tunnel 狀態:", SwingConstants.LEFT), BorderLayout.NORTH);
        JScrollPane tunnelStateScroll = new JScrollPane(tunnelStateField);
        tunnelStateScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tunnelStateScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        uiTunnelCenter.add(tunnelStateScroll, BorderLayout.CENTER);
        tunnelPanel.add(uiTunnelCenter, BorderLayout.CENTER);
        updateTunnelStateField();

        // --- serverPanel ---
        serverPanel.removeAll();
        totalVisitorLabel = new JLabel("總訪客數: " + totalVisitor);
        totalVisitorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        totalVisitorLabel.setFont(new Font("Monospaced", Font.BOLD, 16));

        fileTree = buildFileTree();
        fileTreeScroll = new JScrollPane(fileTree);

        fileVisitLabel = new JLabel("請選擇檔案或資料夾以查看訪問次數");
        fileVisitLabel.setHorizontalAlignment(SwingConstants.CENTER);

        uiServerCenter = new JPanel(new BorderLayout());
        uiServerCenter.add(totalVisitorLabel, BorderLayout.NORTH);
        uiServerCenter.add(fileTreeScroll, BorderLayout.CENTER);
        uiServerCenter.add(fileVisitLabel, BorderLayout.SOUTH);
        serverPanel.add(uiServerCenter, BorderLayout.CENTER);

        serverPanel.revalidate();
        serverPanel.repaint();
        tunnelPanel.revalidate();
        tunnelPanel.repaint();
    }

    // === 原有：consoleMode（保持原樣，補充 currentMode 切換）===
    private void consoleMode() {
        currentMode = MODE.CONSOLE;

        serverPanel.removeAll();
        tunnelPanel.removeAll();

        serverPanel.add(serverScroll, BorderLayout.CENTER);
        tunnelPanel.add(tunnelScroll, BorderLayout.CENTER);
        // copyButton 已在 toolList，tunnelPanel NORTH 不重複加
        tunnelPanel.add(new JPanel(), BorderLayout.NORTH); // 保持 BorderLayout 結構對稱

        serverPanel.revalidate();
        serverPanel.repaint();
        tunnelPanel.revalidate();
        tunnelPanel.repaint();
    }

    // === 新增：switchMode ===
    private void switchMode() {
        if (currentMode == MODE.CONSOLE) {
            UI_Mode();
        } else {
            consoleMode();
        }
        switchModeButton.setText(currentMode == MODE.CONSOLE ? "切換至 UI 模式" : "切換至 Console 模式");
    }

    public void createAndShowGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        frame = new JFrame("Python Server + Cloudflare Tunnel Monitor");
        frame.setIconImage(new ImageIcon(originalPath + "\\..\\resource\\logo.png").getImage());
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLayout(new BorderLayout()); // 改為 BorderLayout 以容納 toolList(NORTH)

        // --- 中央區域：左右兩欄 ---
        centerPanel = new JPanel(new GridLayout(1, 2));

        serverOutput = new JTextArea();
        serverOutput.setBackground(Color.black);
        serverOutput.setForeground(Color.green);
        serverOutput.setEditable(false);
        serverOutput.setName("SERVER");
        serverScroll = new JScrollPane(serverOutput);

        serverPanel = new JPanel(new BorderLayout());

        tunnelOutput = new JTextArea();
        tunnelOutput.setBackground(Color.black);
        tunnelOutput.setForeground(Color.green);
        tunnelOutput.setEditable(false);
        tunnelOutput.setName("CLOUDFLARD");
        tunnelScroll = new JScrollPane(tunnelOutput);

        copyButton = new JButton("複製臨時連結並顯示 QRCode");
        copyButton.addActionListener(e -> copyTunnelLink());

        tunnelPanel = new JPanel(new BorderLayout());

        centerPanel.add(serverPanel);
        centerPanel.add(tunnelPanel);

        // --- 新增：toolList (NORTH) ---
        toolList = new JPanel(new FlowLayout(FlowLayout.LEFT));

        toolList.add(copyButton);

        switchModeButton = new JButton("切換至 UI 模式");
        switchModeButton.addActionListener(e -> switchMode());
        toolList.add(switchModeButton);

        int port = getFreePort();

        frame.add(toolList, BorderLayout.NORTH);
        frame.setVisible(true);

        // --- 初始化 mode ---
        currentMode = MODE.CONSOLE;
        loadPanel = new JPanel(new BorderLayout());
        JTextField loadTextField=new JTextField("loading...");
        loadTextField.setFont(new Font("Monospaced", Font.BOLD, 24));
        loadTextField.setEditable(false);
        loadTextField.setHorizontalAlignment(JTextField.CENTER);
        loadPanel.add(loadTextField,BorderLayout.CENTER);
        frame.add(loadPanel, BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();
        SwingUtilities.invokeLater(() -> {
            UI_Mode();
            switchModeButton.setText("切換至 Console 模式");
        });
        pythonProcess = startProcess(
                new String[]{
                        originalPath + "\\tool\\python-3.14.0-embed-amd64\\python.exe",
                        originalPath + "\\tool\\server.py",
                        Integer.toString(port)
                },
                path,
                serverOutput
        );

        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            tunnelProcess = startProcess(
                    new String[]{
                            originalPath + "\\tool\\cloudflared.exe",
                            "tunnel",
                            "--url",
                            "http://localhost:" + port,
                            "--protocol",
                            "http2"
                    },
                    path,
                    tunnelOutput
            );
        }).start();

        tunnelOutput.append("[FILESHARING] [" + LocalDateTime.now() + "]\n");
        tunnelOutput.append("[FILESHARING] the tunnel path to folder: " + path + "\n");
        tunnelOutput.append("[FILESHARING] fileSharing is using the port " + port + "\n");

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                shutdownProcesses();
                System.exit(0);
            }
        });
    }

    public enum STATE { LOADING, SUCCESS, net_ERROR,req_ERROR }
    public STATE tunnelState;
    int totalVisitor = 0;

    private Process startProcess(String[] command, String dir, JTextArea outputArea) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(dir));
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), "UTF-8")
                )) {
                    String line;
                    tunnelState = STATE.LOADING;
                    while ((line = reader.readLine()) != null) {
                        String finalLine = line;
                        if (line.indexOf(".trycloudflare.com") != -1) {
                            link = line.substring(line.indexOf("https://"), line.indexOf(".trycloudflare.com") + 18);
                            tunnelState = STATE.SUCCESS;
                            updateTunnelStateField(); // 新增接口
                        }
                        if (line.equals("failed to request quick Tunnel: Post \"https://api.trycloudflare.com/tunnel\": dial tcp: lookup api.trycloudflare.com: no such host")) {
                            tunnelState = STATE.net_ERROR;
                            updateTunnelStateField();
                        }
                        if (line.equals("failed to unmarshal quick Tunnel: invalid character 'e' looking for beginning of value")) {
                        	tunnelState = STATE.req_ERROR;
                        	updateTunnelStateField();
                        }
                        String[] code;
                        try {
                            code = line.split(" ");
                            if (code[code.length - 2].equals("404")) {
                                totalVisitor += 1;
                                updateTotalVisitorLabel();
                            }
                            if (code[code.length - 2].equals("200") || code[code.length - 2].equals("404")) {
                                try {
                                    String reqPath = code[6];
                                    if (reqPath.startsWith("/")) reqPath = reqPath.substring(1);
                                    File visited = new File(path, reqPath);
                                    String absVisited = visited.getAbsolutePath();
                                    fileVisitMap.put(absVisited, fileVisitMap.getOrDefault(absVisited, 0) + 1);
                                } catch (Exception ignored) {}
                            }
                        } catch (Exception e) {}
                        if (outputArea != null)
                            SwingUtilities.invokeLater(() -> outputArea.append("[" + outputArea.getName() + "] " + finalLine + "\n"));
                    }
                } catch (IOException ignored) {}
            }).start();
            return process;
        } catch (IOException e) {
            if (outputArea != null)
                outputArea.append("ERROR: " + e.getMessage() + "\n");
            return null;
        }
    }

    private void shutdownProcesses() {
        killIfAlive(pythonProcess);
        killIfAlive(tunnelProcess);
    }

    private void killIfAlive(Process process) {
        if (process == null) return;
        try {
            if (process.isAlive()) {
                process.destroy();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        } catch (Exception ignored) {}
    }

    private void copyTunnelLink() {
        if (link != null) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(link), null
            );
            showQRCodeWindow(link);
            JOptionPane.showMessageDialog(null, "已複製臨時連結:\n" + link);
            return;
        }
        JOptionPane.showMessageDialog(null, "未找到臨時連結，請確認 tunnel 是否啟動。");
    }

    private void showQRCodeWindow(String link) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(link, BarcodeFormat.QR_CODE, 300, 300);
            Image qrImage = MatrixToImageWriter.toBufferedImage(matrix);

            JLabel label = new JLabel(new ImageIcon(qrImage));
            JFrame qrFrame = new JFrame("QRCode for Link");
            qrFrame.setSize(350, 380);
            qrFrame.add(label);
            qrFrame.setVisible(true);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "QR 生成失敗: " + e.getMessage());
        }
    }
}

class FolderSelector {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("choose the folder");
            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                ServerTunnelGUI gui = new ServerTunnelGUI();
                gui.path = path;
                gui.createAndShowGUI();
            }
        });
    }
}