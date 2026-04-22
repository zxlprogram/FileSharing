import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
public class ServerTunnelGUI {
    private Random r = new Random();
    private String rateLimitMessage = null;
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

    public boolean usePassword = false;
    public String password = null;

    public enum MODE { UI, CONSOLE }
    public MODE currentMode;

    private JTextField tunnelStateField;
    private JLabel totalVisitorLabel;
    private JTree fileTree;
    private JScrollPane fileTreeScroll;
    private JLabel fileVisitLabel;
    private JPanel uiServerCenter;
    private JPanel uiTunnelCenter;

    private JPanel toolList;
    private JButton switchModeButton;

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
                    tunnelStateField.setText(rateLimitMessage != null ? rateLimitMessage : "tunnel建立的頻率過高，請稍後再試");
                    tunnelStateField.setForeground(Color.RED);
                    break;
            }
        });
    }

    private void updateTotalVisitorLabel() {
        if (totalVisitorLabel == null) return;
        SwingUtilities.invokeLater(() -> totalVisitorLabel.setText("總訪客數: " + totalVisitor));
    }

    private void buildFileTreeNode(File file, javax.swing.tree.DefaultMutableTreeNode parentNode, JTree tree) {
        File[] children = file.listFiles();
        if (children == null) return;

        java.util.Arrays.sort(children, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareTo(b.getName());
        });

        for (File child : children) {
            javax.swing.tree.DefaultMutableTreeNode childNode =
                new javax.swing.tree.DefaultMutableTreeNode();
            childNode.setUserObject(child);
            SwingUtilities.invokeLater(() -> {
                ((javax.swing.tree.DefaultTreeModel) tree.getModel())
                    .insertNodeInto(childNode, parentNode, parentNode.getChildCount());
            });
        }
    }
    private void startMode() {
        javax.swing.tree.DefaultMutableTreeNode root =
                new javax.swing.tree.DefaultMutableTreeNode(
                    path.isEmpty() ? path : new File(path).getName().isEmpty() ? path : new File(path).getName()
                );
            root.setUserObject(new File(path));

            fileTree = new JTree(root);
            fileTree.setCellRenderer(new javax.swing.tree.DefaultTreeCellRenderer() {
            	@SuppressWarnings("unused")
				private FileSystemView fsv = FileSystemView.getFileSystemView();
                @Override
                public Component getTreeCellRendererComponent(JTree tree, Object value,
                        boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                    super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                    if (value instanceof javax.swing.tree.DefaultMutableTreeNode) {
                        Object uo = ((javax.swing.tree.DefaultMutableTreeNode) value).getUserObject();
                        if (uo instanceof File) {
                            File f = (File) uo;
                            setText(f.getName().isEmpty() ? f.getAbsolutePath() : f.getName());
                            setIcon(fsv.getSystemIcon(f));
                            //上面那個會變美觀但會卡
                        }
                    }
                    return this;
                }
            });

            fileTree.addTreeSelectionListener(e -> {
                javax.swing.tree.DefaultMutableTreeNode node =
                    (javax.swing.tree.DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
                if (node == null || fileVisitLabel == null) return;
                Object uo = node.getUserObject();
                if (uo instanceof File) {
                    File selected = (File) uo;
                    String absPath = selected.getAbsolutePath();
                    int count = fileVisitMap.getOrDefault(absPath, 0);
                    fileVisitLabel.setText("「" + selected.getName() + "」被訪問次數: " + count);
                }
            });

            JTree treeRef = fileTree;

            // 展開時載入孫子
            fileTree.addTreeExpansionListener(new javax.swing.event.TreeExpansionListener() {
                @Override
                public void treeExpanded(javax.swing.event.TreeExpansionEvent event) {
                    javax.swing.tree.DefaultMutableTreeNode node =
                        (javax.swing.tree.DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                    for (int i = 0; i < node.getChildCount(); i++) {
                        javax.swing.tree.DefaultMutableTreeNode child =
                            (javax.swing.tree.DefaultMutableTreeNode) node.getChildAt(i);
                        Object uo = child.getUserObject();
                        if (uo instanceof File && ((File) uo).isDirectory() && child.getChildCount() == 0) {
                            File childDir = (File) uo;
                            new Thread(() -> buildFileTreeNode(childDir, child, treeRef)).start();
                        }
                    }
                }

                @Override
                public void treeCollapsed(javax.swing.event.TreeExpansionEvent event) {}
            });

            // 建第一層，完成後預載第二層
            new Thread(() -> {
                buildFileTreeNode(new File(path), root, treeRef);
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                SwingUtilities.invokeLater(() -> {
                    for (int i = 0; i < root.getChildCount(); i++) {
                        javax.swing.tree.DefaultMutableTreeNode child =
                            (javax.swing.tree.DefaultMutableTreeNode) root.getChildAt(i);
                        Object uo = child.getUserObject();
                        if (uo instanceof File && ((File) uo).isDirectory()) {
                            File childDir = (File) uo;
                            new Thread(() -> buildFileTreeNode(childDir, child, treeRef)).start();
                        }
                    }
                });
            }).start();

        frame.remove(loadPanel);
        frame.add(centerPanel, BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();
    }

    private void UI_Mode() {
        currentMode = MODE.UI;

        // Tunnel 面板
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

        JLabel pwLabel = new JLabel(usePassword
            ? "🔒 密碼保護：已啟用"
            : "🔓 密碼保護：未啟用");
        pwLabel.setHorizontalAlignment(SwingConstants.CENTER);
        pwLabel.setForeground(usePassword ? new Color(0, 120, 0) : Color.GRAY);
        uiTunnelCenter.add(pwLabel, BorderLayout.SOUTH);

        tunnelPanel.add(uiTunnelCenter, BorderLayout.CENTER);
        updateTunnelStateField();

        // Server 面板
        serverPanel.removeAll();
        totalVisitorLabel = new JLabel("總訪客數: " + totalVisitor);
        totalVisitorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        totalVisitorLabel.setFont(new Font("Monospaced", Font.BOLD, 16));

        fileVisitLabel = new JLabel("請選擇檔案或資料夾以查看訪問次數");
        fileVisitLabel.setHorizontalAlignment(SwingConstants.CENTER);


        fileTreeScroll = new JScrollPane(fileTree);

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

    private void consoleMode() {
        currentMode = MODE.CONSOLE;

        serverPanel.removeAll();
        tunnelPanel.removeAll();

        serverPanel.add(serverScroll, BorderLayout.CENTER);
        tunnelPanel.add(tunnelScroll, BorderLayout.CENTER);
        tunnelPanel.add(new JPanel(), BorderLayout.NORTH);

        serverPanel.revalidate();
        serverPanel.repaint();
        tunnelPanel.revalidate();
        tunnelPanel.repaint();
    }

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
        frame.setLayout(new BorderLayout());

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

        toolList = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolList.add(copyButton);

        switchModeButton = new JButton("切換至 UI 模式");
        switchModeButton.addActionListener(e -> switchMode());
        toolList.add(switchModeButton);

        int port = getFreePort();

        frame.add(toolList, BorderLayout.NORTH);
        frame.setVisible(true);

        currentMode = MODE.CONSOLE;
        loadPanel = new JPanel(new BorderLayout());
        JTextField loadTextField = new JTextField("loading...");
        loadTextField.setFont(new Font("Monospaced", Font.BOLD, 24));
        loadTextField.setEditable(false);
        loadTextField.setHorizontalAlignment(JTextField.CENTER);
        loadPanel.add(loadTextField, BorderLayout.CENTER);
        frame.add(loadPanel, BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();

        SwingUtilities.invokeLater(() -> {
        	startMode();
            UI_Mode();
            switchModeButton.setText("切換至 Console 模式");
        });

        List<String> pythonCmd = new ArrayList<>(Arrays.asList(
            originalPath + "\\tool\\python-3.14.0-embed-amd64\\python.exe",
            originalPath + "\\tool\\server.py",
            Integer.toString(port),
            Boolean.toString(usePassword)
        ));
        if (usePassword && password != null && !password.isEmpty()) {
            pythonCmd.add(password);
        }
        pythonProcess = startProcess(pythonCmd.toArray(new String[0]), path, serverOutput);

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
        tunnelOutput.append("[FILESHARING] password protection: " + (usePassword ? "enabled" : "disabled") + "\n");

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                shutdownProcesses();
                System.exit(0);
            }
        });
    }

    public enum STATE { LOADING, SUCCESS, net_ERROR, req_ERROR }
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
                            updateTunnelStateField();
                        }
                        if (line.equals("failed to request quick Tunnel: Post \"https://api.trycloudflare.com/tunnel\": dial tcp: lookup api.trycloudflare.com: no such host")) {
                            tunnelState = STATE.net_ERROR;
                            updateTunnelStateField();
                        }
                        if (line.contains("status_code=\"429 Too Many Requests\"")) {
                            tunnelState = STATE.req_ERROR;
                            new Thread(() -> {
                                for (int attempt = 0; attempt < 10; attempt++) {
                                    try {
                                        Process curlProcess = new ProcessBuilder(
                                            "curl.exe", "-s", "-o", "NUL", "-D", "-",
                                            "-X", "POST", "https://api.trycloudflare.com/tunnel"
                                        ).start();

                                        BufferedReader curlReader = new BufferedReader(
                                            new InputStreamReader(curlProcess.getInputStream())
                                        );
                                        String curlLine;
                                        boolean got429 = false;
                                        while ((curlLine = curlReader.readLine()) != null) {
                                            if (curlLine.contains("429")) got429 = true;
                                            if (got429 && curlLine.toLowerCase().startsWith("retry-after:")) {
                                                int seconds = Integer.parseInt(curlLine.split(":")[1].trim());
                                                LocalTime unblockTime = LocalTime.now().plusSeconds(seconds);
                                                String formatted = unblockTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                                                rateLimitMessage = "tunnel建立的頻率過高，請在 " + formatted + " 再試";
                                                break;
                                            }
                                        }
                                        if (rateLimitMessage != null) break; // 成功拿到就停
                                        Thread.sleep(500); // 沒拿到就等 0.5 秒再試
                                    } catch (Exception ex) {
                                        // 繼續重試
                                    }
                                }
                                if (rateLimitMessage == null) {
                                    rateLimitMessage = "tunnel建立的頻率過高，請稍後再試";
                                }
                                while (tunnelStateField == null) {
                                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                                }
                                updateTunnelStateField();
                            }).start();
                        }          
                        try {
                            String[] code = line.split(" ");
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
            qrFrame.setIconImage(new ImageIcon(originalPath + "\\..\\resource\\logo.png").getImage());
            qrFrame.setSize(350, 380);
            qrFrame.add(label);
            qrFrame.setVisible(true);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "QR 生成失敗: " + e.getMessage());
        }
    }
}

class FolderSelector {
    public static String originalPath;
    public static long getFolderSize(String folderPath) throws IOException {
        AtomicLong total = new AtomicLong(0);

        Files.walkFileTree(Paths.get(folderPath),
            java.util.EnumSet.noneOf(FileVisitOption.class),
            Integer.MAX_VALUE,
            new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isSymbolicLink()) {
                        total.addAndGet(attrs.size());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    System.err.println("跳過：" + file);
                    return FileVisitResult.CONTINUE;
                }
            });

        return total.get() / 1024;
    }

    public static void main(String[] args) {
        try {
            originalPath = new File(
                    ServerTunnelGUI.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getParent();
        } catch (Exception e) {
            e.printStackTrace();
            originalPath = "";
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> {
            JDialog dialog = new JDialog((Frame) null, "選擇分享資料夾", true);
            dialog.setIconImage(new ImageIcon(originalPath + "\\..\\resource\\logo.png").getImage());
            dialog.setSize(650, 500);
            dialog.setLocationRelativeTo(null);
            dialog.setLayout(new BorderLayout());
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    dialog.dispose();
                    System.exit(0);
                }
            });
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("選擇要分享的資料夾");
            chooser.setControlButtonsAreShown(false);
            dialog.add(chooser, BorderLayout.CENTER);

            JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));

            JCheckBox passwordCheckBox = new JCheckBox("啟用密碼保護");

            JLabel passwordLabel = new JLabel("密碼：");
            passwordLabel.setVisible(false);

            JTextField passwordField = new JTextField(16);
            passwordField.setToolTipText("請輸入密碼");
            passwordField.setVisible(false);

            passwordCheckBox.addActionListener(e -> {
                boolean checked = passwordCheckBox.isSelected();
                passwordLabel.setVisible(checked);
                passwordField.setVisible(checked);
                southPanel.revalidate();
                southPanel.repaint();
            });

            JButton confirmButton = new JButton("開啟");
            confirmButton.addActionListener(e -> {
                File selected = chooser.getSelectedFile();
                if (selected == null) {
                    selected = chooser.getCurrentDirectory();
                }
                if (selected == null) {
                    JOptionPane.showMessageDialog(dialog, "請先選擇一個資料夾。");
                    return;
                }

                boolean usePassword = passwordCheckBox.isSelected();
                String password = usePassword ? passwordField.getText().trim() : null;

                if (usePassword && (password == null || password.isEmpty())) {
                    int confirm = JOptionPane.showConfirmDialog(
                        dialog,
                        "密碼欄位為空，確定要以空字串作為密碼嗎？",
                        "密碼確認",
                        JOptionPane.YES_NO_OPTION
                    );
                    if (confirm != JOptionPane.YES_OPTION) return;
                }

                dialog.dispose();

                String finalPath = selected.getAbsolutePath();
                ServerTunnelGUI gui = new ServerTunnelGUI();
                gui.path = finalPath;
                gui.usePassword = usePassword;
                gui.password = password;
                gui.createAndShowGUI();
            });

            JButton cancelButton = new JButton("取消");
            cancelButton.addActionListener(e -> {
                dialog.dispose();
                System.exit(0);
            });

            southPanel.add(passwordCheckBox);
            southPanel.add(passwordLabel);
            southPanel.add(passwordField);
            southPanel.add(Box.createHorizontalStrut(30));
            southPanel.add(confirmButton);
            southPanel.add(cancelButton);

            JPanel southWrapper = new JPanel(new BorderLayout());
            southWrapper.add(new JSeparator(), BorderLayout.NORTH);
            southWrapper.add(southPanel, BorderLayout.CENTER);

            dialog.add(southWrapper, BorderLayout.SOUTH);
            dialog.setVisible(true);
        });
    }
}