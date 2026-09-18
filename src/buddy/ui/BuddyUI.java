package buddy.ui;

import buddy.commands.Commands;
import buddy.logger.ActivityLogger;
import buddy.memory.MemoryManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BuddyUI {

    private final Commands commands;
    private final ActivityLogger activityLogger = new ActivityLogger();
    private final MemoryManager memoryManager = new MemoryManager();

    private JFrame frame;
    private JLabel statusLabel;
    private JLabel timeLabel;
    private JTextArea conversationArea;
    private JLabel cpuLabel;
    private JLabel memoryLabel;
    private JLabel diskLabel;
    private JTextField commandField;
    private JLabel brainStatusLabel;
    private JLabel brainDetailLabel;
    private JLabel coreTitleLabel;
    private JLabel waveformLabel;
    private DefaultListModel<String> activityModel;
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private Timer clockTimer;
    private Timer systemTimer;
    private BuddyState currentState = BuddyState.READY;

    public BuddyUI(Commands commands) {
        this.commands = commands;
    }

    public void show() {
        SwingUtilities.invokeLater(this::createUI);
    }

    public void minimize() {
        if (frame != null) {
            SwingUtilities.invokeLater(() -> frame.setState(Frame.ICONIFIED));
        }
    }

    private void createUI() {
        frame = new JFrame("Buddy Command Center");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(1480, 920);
        frame.setMinimumSize(new Dimension(1200, 760));
        frame.setLocationRelativeTo(null);
        frame.setBackground(new Color(4, 12, 22));

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                frame.setVisible(false);
            }
        });
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                frame.revalidate();
                frame.repaint();
            }
        });

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBackground(new Color(4, 12, 22));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        root.add(createTopBar(), BorderLayout.NORTH);
        root.add(createMainWorkspace(), BorderLayout.CENTER);
        root.add(createBottomBar(), BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);

        startTimers();
        setState(BuddyState.READY);
        showBrainStatus("READY", "Listening for the next command.");
        addActivity("SYSTEM", "Buddy started", "System ready", BuddyState.READY);
    }

    private JPanel createTopBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(9, 18, 29));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(32, 58, 74), 1),
                new EmptyBorder(10, 18, 10, 18)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        left.setOpaque(false);

        JLabel logo = new JLabel("B");
        logo.setForeground(new Color(188, 235, 255));
        logo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        logo.setOpaque(true);
        logo.setBackground(new Color(22, 46, 63));
        logo.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JLabel title = new JLabel("BUDDY");
        title.setForeground(new Color(232, 245, 250));
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));

        JLabel subtitle = new JLabel("AI COMMAND CENTER");
        subtitle.setForeground(new Color(136, 167, 182));
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);

        left.add(logo);
        left.add(title);
        left.add(subtitle);
        panel.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        right.setOpaque(false);

        statusLabel = new JLabel("READY");
        statusLabel.setForeground(new Color(120, 240, 170));
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(12, 43, 31));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        timeLabel = new JLabel("--:--:--");
        timeLabel.setForeground(new Color(201, 214, 224));
        timeLabel.setFont(new Font("Consolas", Font.PLAIN, 15));

        JButton settings = createTopButton("SET");
        JButton minimize = createTopButton("_");
        JButton maximize = createTopButton("[]");
        JButton close = createTopButton("X");
        close.setForeground(new Color(255, 150, 150));

        settings.addActionListener(e -> showView("SETTINGS"));
        minimize.addActionListener(e -> frame.setState(Frame.ICONIFIED));
        maximize.addActionListener(e -> {
            if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
                frame.setExtendedState(Frame.NORMAL);
            } else {
                frame.setExtendedState(Frame.MAXIMIZED_BOTH);
            }
        });
        close.addActionListener(e -> frame.setVisible(false));

        right.add(statusLabel);
        right.add(timeLabel);
        right.add(settings);
        right.add(minimize);
        right.add(maximize);
        right.add(close);

        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private JPanel createMainWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(16, 16));
        workspace.setBackground(new Color(4, 12, 22));

        JPanel sidebar = createSidebar();
        workspace.add(sidebar, BorderLayout.WEST);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBackground(new Color(4, 12, 22));
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        cardPanel.add(createHomeView(), "HOME");
        cardPanel.add(createCoreView(), "CORE");
        cardPanel.add(createActivityView(), "ACTIVITY");
        cardPanel.add(createMemoryView(), "MEMORY");
        cardPanel.add(createFilesView(), "FILES");
        cardPanel.add(createWebView(), "WEB");
        cardPanel.add(createSettingsView(), "SETTINGS");

        content.add(cardPanel, BorderLayout.CENTER);
        workspace.add(content, BorderLayout.CENTER);

        cardLayout.show(cardPanel, "HOME");
        return workspace;
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(230, 0));
        sidebar.setBackground(new Color(8, 16, 25));
        sidebar.setBorder(new EmptyBorder(20, 12, 18, 10));

        JLabel brand = new JLabel("BUDDY");
        brand.setForeground(new Color(118, 214, 255));
        brand.setFont(new Font("Segoe UI", Font.BOLD, 24));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.setBorder(new EmptyBorder(0, 6, 18, 0));
        sidebar.add(brand);

        String[] items = { "HOME", "AI CORE", "ACTIVITY", "MEMORY", "FILES", "WEB", "SETTINGS" };
        for (int i = 0; i < items.length; i++) {
            String item = items[i];
            JButton button = createSidebarButton(item);
            if (i == 0) {
                button.setBackground(new Color(17, 33, 44));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(62, 130, 182), 1),
                        new EmptyBorder(10, 10, 10, 10)));
            }
            button.addActionListener(e -> showView(item));
            sidebar.add(Box.createVerticalStrut(6));
            sidebar.add(button);
        }

        sidebar.add(Box.createVerticalGlue());
        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        status.setOpaque(false);
        JLabel led = new JLabel("•");
        led.setForeground(new Color(80, 220, 150));
        led.setFont(new Font("Segoe UI", Font.BOLD, 18));
        JLabel text = new JLabel("SYSTEM ONLINE");
        text.setForeground(new Color(220, 235, 246));
        text.setFont(new Font("Segoe UI", Font.BOLD, 11));
        status.add(led);
        status.add(text);
        sidebar.add(status);
        return sidebar;
    }

    private void showView(String item) {
        if (cardLayout == null || cardPanel == null) {
            return;
        }
        String key = switch (item) {
            case "AI CORE" -> "CORE";
            case "ACTIVITY" -> "ACTIVITY";
            case "MEMORY" -> "MEMORY";
            case "FILES" -> "FILES";
            case "WEB" -> "WEB";
            case "SETTINGS" -> "SETTINGS";
            default -> "HOME";
        };
        cardLayout.show(cardPanel, key);
    }

    private JPanel createHomeView() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBackground(new Color(7, 18, 27));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel coreCard = new JPanel(new BorderLayout(16, 16));
        coreCard.setBackground(new Color(9, 21, 34));
        coreCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 72, 90), 1),
                new EmptyBorder(18, 18, 18, 18)));

        JPanel coreGraphic = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int r = Math.min(getWidth(), getHeight()) / 2 - 26;

                g2.setColor(new Color(11, 25, 35, 200));
                g2.fillOval(cx - r, cy - r, r * 2, r * 2);

                g2.setColor(new Color(88, 186, 255, 120));
                g2.setStroke(new BasicStroke(1.3f));
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2 * i / 24;
                    int x = (int) (cx + Math.cos(a) * r);
                    int y = (int) (cy + Math.sin(a) * r);
                    g2.drawLine(cx, cy, x, y);
                }

                g2.setColor(new Color(110, 210, 255, 140));
                g2.drawOval(cx - r + 20, cy - r + 20, (r - 20) * 2, (r - 20) * 2);
                g2.drawOval(cx - r + 48, cy - r + 48, (r - 48) * 2, (r - 48) * 2);

                g2.setColor(new Color(35, 124, 170));
                g2.fillOval(cx - 62, cy - 62, 124, 124);

                g2.setColor(new Color(225, 245, 255));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 30));
                g2.drawString("BUDDY", cx - 78, cy + 12);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                g2.drawString(currentState.name(), cx - 36, cy + 34);

                g2.setStroke(new BasicStroke(2f));
                g2.setColor(new Color(112, 212, 255, 180));
                g2.drawOval(cx - 90, cy - 90, 180, 180);
                g2.dispose();
            }
        };
        coreGraphic.setPreferredSize(new Dimension(420, 260));
        coreGraphic.setOpaque(false);

        JPanel statusPanel = new JPanel(new BorderLayout(8, 8));
        statusPanel.setOpaque(false);
        brainStatusLabel = new JLabel("READY");
        brainStatusLabel.setForeground(new Color(123, 219, 255));
        brainStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        brainDetailLabel = new JLabel("Listening for the next command.");
        brainDetailLabel.setForeground(new Color(205, 221, 240));
        brainDetailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusPanel.add(brainStatusLabel, BorderLayout.NORTH);
        statusPanel.add(brainDetailLabel, BorderLayout.CENTER);

        coreCard.add(coreGraphic, BorderLayout.CENTER);
        coreCard.add(statusPanel, BorderLayout.SOUTH);

        JPanel sideStack = new JPanel();
        sideStack.setLayout(new BoxLayout(sideStack, BoxLayout.Y_AXIS));
        sideStack.setOpaque(false);

        sideStack.add(createMetricCard("CPU", "18%", "System load", new Color(90, 210, 255)));
        sideStack.add(Box.createVerticalStrut(12));
        sideStack.add(createMetricCard("MEM", "42%", "Memory relays", new Color(86, 227, 163)));
        sideStack.add(Box.createVerticalStrut(12));
        sideStack.add(createMetricCard("STORAGE", "67%", "Drive health", new Color(255, 191, 94)));

        JSplitPane topRow = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, coreCard, sideStack);
        topRow.setBorder(null);
        topRow.setOpaque(false);
        topRow.setContinuousLayout(true);
        topRow.setResizeWeight(1.0);
        topRow.setDividerSize(10);
        topRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel conversation = new JPanel(new BorderLayout());
        conversation.setBackground(new Color(9, 21, 34));
        conversation.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 72, 90), 1),
                new EmptyBorder(12, 12, 12, 12)));
        JLabel convoTitle = new JLabel("VOICE CONVERSATION");
        convoTitle.setForeground(new Color(123, 219, 255));
        convoTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        conversationArea = new JTextArea();
        conversationArea.setEditable(false);
        conversationArea.setOpaque(false);
        conversationArea.setForeground(new Color(228, 240, 249));
        conversationArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        conversationArea.setLineWrap(true);
        conversationArea.setWrapStyleWord(true);
        conversation.add(convoTitle, BorderLayout.NORTH);
        conversation.add(new JScrollPane(conversationArea), BorderLayout.CENTER);

        JPanel activityPanel = new JPanel(new BorderLayout());
        activityPanel.setBackground(new Color(9, 21, 34));
        activityPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 72, 90), 1),
                new EmptyBorder(12, 12, 12, 12)));
        JLabel activityTitle = new JLabel("RECENT ACTIVITY");
        activityTitle.setForeground(new Color(123, 219, 255));
        activityTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        DefaultListModel<String> recentModel = new DefaultListModel<>();
        recentModel.addElement("[voice] Listening ready");
        recentModel.addElement("[brain] Understanding request");
        recentModel.addElement("[power] Executing action");
        recentModel.addElement("[OK] Ready");
        JList<String> recentList = new JList<>(recentModel);
        recentList.setBackground(new Color(9, 21, 34));
        recentList.setForeground(new Color(220, 233, 244));
        recentList.setBorder(null);
        recentList.setSelectionBackground(new Color(18, 54, 71));
        activityPanel.add(activityTitle, BorderLayout.NORTH);
        activityPanel.add(new JScrollPane(recentList), BorderLayout.CENTER);

        JSplitPane bottomRow = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, conversation, activityPanel);
        bottomRow.setBorder(null);
        bottomRow.setOpaque(false);
        bottomRow.setContinuousLayout(true);
        bottomRow.setResizeWeight(1.0);
        bottomRow.setDividerSize(10);
        bottomRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        panel.add(topRow, BorderLayout.NORTH);
        panel.add(bottomRow, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCoreView() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(7, 18, 27));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel inner = new JPanel(new BorderLayout());
        inner.setBackground(new Color(9, 21, 34));
        inner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 72, 90), 1),
                new EmptyBorder(20, 20, 20, 20)));

        coreTitleLabel = new JLabel("READY");
        coreTitleLabel.setForeground(new Color(123, 219, 255));
        coreTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));

        waveformLabel = new JLabel(getWaveformForState(BuddyState.READY));
        waveformLabel.setForeground(new Color(123, 219, 255));
        waveformLabel.setFont(new Font("Segoe UI", Font.PLAIN, 28));
        waveformLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel visual = new JPanel(new GridLayout(3, 1, 8, 8));
        visual.setOpaque(false);
        visual.add(new JLabel("O", SwingConstants.CENTER));
        visual.add(new JLabel("B U D D Y", SwingConstants.CENTER));
        visual.add(waveformLabel);

        inner.add(coreTitleLabel, BorderLayout.NORTH);
        inner.add(visual, BorderLayout.CENTER);
        panel.add(inner, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createActivityView() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(7, 18, 27));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel inner = new JPanel(new BorderLayout());
        inner.setBackground(new Color(9, 21, 34));
        inner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 72, 90), 1),
                new EmptyBorder(14, 14, 14, 14)));

        JLabel title = new JLabel("ACTIVITY TIMELINE");
        title.setForeground(new Color(123, 219, 255));
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setBorder(new EmptyBorder(0, 0, 12, 0));

        activityModel = new DefaultListModel<>();
        activityModel.addElement("[" + stamp() + "] SYSTEM - Buddy started - READY");
        activityModel.addElement("[" + stamp() + "] VOICE - Listening - READY");

        JList<String> list = new JList<>(activityModel);
        list.setBackground(new Color(9, 21, 34));
        list.setForeground(new Color(228, 240, 249));
        list.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        list.setSelectionBackground(new Color(18, 54, 71));
        list.setFixedCellHeight(36);

        inner.add(title, BorderLayout.NORTH);
        inner.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(inner, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createMemoryView() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBackground(new Color(7, 18, 27));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(new Color(9, 21, 34));
        body.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 72, 90), 1),
                new EmptyBorder(14, 14, 14, 14)));

        JTextArea area = new JTextArea(loadMemorySummary());
        area.setEditable(false);
        area.setBackground(new Color(9, 21, 34));
        area.setForeground(new Color(228, 240, 249));
        area.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        body.add(new JScrollPane(area), BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.setBackground(new Color(9, 21, 34));
        actions.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 72, 90), 1),
                new EmptyBorder(14, 14, 14, 14)));

        JButton memoryFile = new JButton("Open User Memory");
        JButton websiteFile = new JButton("Open Websites");
        JButton appFile = new JButton("Open Applications");
        JButton refresh = new JButton("Refresh Memory");
        for (JButton b : new JButton[] { memoryFile, websiteFile, appFile, refresh }) {
            b.setFocusPainted(false);
            b.setBackground(new Color(20, 61, 79));
            b.setForeground(new Color(228, 240, 249));
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            b.addActionListener(e -> handleMemoryAction(b.getText()));
        }
        actions.add(memoryFile);
        actions.add(Box.createVerticalStrut(8));
        actions.add(websiteFile);
        actions.add(Box.createVerticalStrut(8));
        actions.add(appFile);
        actions.add(Box.createVerticalStrut(8));
        actions.add(refresh);

        panel.add(body, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.EAST);
        return panel;
    }

    private JPanel createFilesView() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(7, 18, 27));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel actions = new JPanel(new GridLayout(4, 1, 12, 12));
        actions.setBackground(new Color(9, 21, 34));
        actions.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 72, 90), 1),
                new EmptyBorder(16, 16, 16, 16)));

        JButton openDesktop = new JButton("Open Desktop");
        JButton openDocuments = new JButton("Open Documents");
        JButton createFolder = new JButton("Create Projects folder");
        JButton openReadme = new JButton("Open README");
        for (JButton b : new JButton[] { openDesktop, openDocuments, createFolder, openReadme }) {
            b.setFocusPainted(false);
            b.setBackground(new Color(20, 61, 79));
            b.setForeground(new Color(228, 240, 249));
            b.addActionListener(e -> runFileAction(b.getText()));
        }

        actions.add(openDesktop);
        actions.add(openDocuments);
        actions.add(createFolder);
        actions.add(openReadme);
        panel.add(actions, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createWebView() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(7, 18, 27));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(new Color(9, 21, 34));
        body.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 72, 90), 1),
                new EmptyBorder(16, 16, 16, 16)));

        JTextArea area = new JTextArea(
                "WEB INTELLIGENCE\n\nStatus: READY\nQuery: none\n\nUse Buddy voice or command input to search the web.");
        area.setEditable(false);
        area.setBackground(new Color(9, 21, 34));
        area.setForeground(new Color(228, 240, 249));
        area.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        body.add(new JScrollPane(area), BorderLayout.CENTER);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSettingsView() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(7, 18, 27));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel settings = new JPanel(new GridLayout(4, 1, 12, 12));
        settings.setBackground(new Color(9, 21, 34));
        settings.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 72, 90), 1),
                new EmptyBorder(16, 16, 16, 16)));

        JButton memory = new JButton("Open memory files");
        JButton log = new JButton("Open activity log");
        JButton pause = new JButton("Pause listening");
        JButton exit = new JButton("Exit Buddy");
        for (JButton b : new JButton[] { memory, log, pause, exit }) {
            b.setFocusPainted(false);
            b.setBackground(new Color(20, 61, 79));
            b.setForeground(new Color(228, 240, 249));
            b.addActionListener(e -> handleSettingsAction(b.getText()));
        }

        settings.add(memory);
        settings.add(log);
        settings.add(pause);
        settings.add(exit);
        panel.add(settings, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createBottomBar() {
        JPanel panel = new JPanel(new BorderLayout(16, 0));
        panel.setBackground(new Color(9, 21, 34));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(35, 58, 72), 1),
                new EmptyBorder(12, 18, 12, 18)));

        JLabel mic = new JLabel("Talk to Buddy");
        mic.setForeground(new Color(123, 219, 255));
        mic.setFont(new Font("Segoe UI", Font.BOLD, 14));

        commandField = new JTextField();
        commandField.setBackground(new Color(18, 28, 39));
        commandField.setForeground(new Color(224, 237, 248));
        commandField.setCaretColor(new Color(123, 219, 255));
        commandField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(44, 69, 85), 1),
                new EmptyBorder(10, 14, 10, 14)));
        commandField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        commandField.addActionListener(e -> submitCommand());

        JButton send = new JButton("Send");
        send.setFocusPainted(false);
        send.setBackground(new Color(20, 78, 103));
        send.setForeground(new Color(235, 245, 250));
        send.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        send.addActionListener(e -> submitCommand());

        panel.add(mic, BorderLayout.WEST);
        panel.add(commandField, BorderLayout.CENTER);
        panel.add(send, BorderLayout.EAST);
        return panel;
    }

    private JButton createSidebarButton(String label) {
        JButton button = new JButton(label);
        button.setFocusPainted(false);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBackground(new Color(12, 22, 35));
        button.setForeground(new Color(220, 235, 246));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(34, 58, 75), 1),
                new EmptyBorder(10, 10, 10, 10)));
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return button;
    }

    private JButton createTopButton(String label) {
        JButton button = new JButton(label);
        button.setFocusPainted(false);
        button.setBackground(new Color(18, 30, 41));
        button.setForeground(new Color(228, 240, 248));
        button.setBorder(BorderFactory.createLineBorder(new Color(41, 62, 75), 1));
        return button;
    }

    private JPanel createMetricCard(String label, String value, String subtext, Color accent) {
        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBackground(new Color(12, 25, 37));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 72, 90), 1),
                new EmptyBorder(12, 12, 12, 12)));
        card.setPreferredSize(new Dimension(150, 92));

        JLabel title = new JLabel(label);
        title.setForeground(new Color(148, 172, 190));
        title.setFont(new Font("Segoe UI", Font.BOLD, 11));

        JLabel stat = new JLabel(value);
        stat.setForeground(accent);
        stat.setFont(new Font("Segoe UI", Font.BOLD, 20));

        JLabel detail = new JLabel(subtext);
        detail.setForeground(new Color(205, 221, 240));
        detail.setFont(new Font("Segoe UI", Font.PLAIN, 10));

        card.add(title, BorderLayout.NORTH);
        card.add(stat, BorderLayout.CENTER);
        card.add(detail, BorderLayout.SOUTH);
        return card;
    }

    private void submitCommand() {
        String command = commandField.getText() == null ? "" : commandField.getText().trim();
        if (command.isEmpty()) {
            return;
        }
        commandField.setText("");
        showUserMessage(command);
        setState(BuddyState.THINKING);
        showBrainStatus("UNDERSTANDING", "Analyzing request...");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                try {
                    return commands.process(command);
                } catch (Exception e) {
                    return "I hit a problem while processing that request. " + e.getMessage();
                }
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    showBuddyResponse(response);
                    setState(BuddyState.READY);
                    showBrainStatus("READY", "Listening for the next command.");
                    addActivity("BUDDY", "Command completed", response, BuddyState.READY);
                } catch (Exception ex) {
                    showError(ex.getMessage());
                    addActivity("ERROR", "Command failed", ex.getMessage(), BuddyState.ERROR);
                }
            }
        };
        worker.execute();
    }

    public void showUserMessage(String message) {
        if (conversationArea == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            conversationArea.append("\n[USER " + stamp() + "]\n" + message + "\n");
            conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
        });
    }

    public void showBuddyResponse(String response) {
        if (conversationArea == null) {
            return;
        }
        final String finalResponse = response == null || response.isBlank() ? "Done." : response;
        SwingUtilities.invokeLater(() -> {
            conversationArea.append("\n[BUDDY " + stamp() + "]\n" + finalResponse + "\n");
            conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
        });
    }

    public void showBrainStatus(String title, String detail) {
        if (brainStatusLabel == null || brainDetailLabel == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            brainStatusLabel.setText(title);
            brainDetailLabel.setText(detail);
        });
    }

    public void showAction(String action) {
        addActivity("ACTION", "Executing", action, BuddyState.EXECUTING);
    }

    public void showWebSearch(String query) {
        addActivity("WEB", "Searching", query, BuddyState.SEARCHING);
    }

    public void showError(String message) {
        if (conversationArea != null) {
            SwingUtilities.invokeLater(() -> {
                conversationArea.append("\n[ERROR " + stamp() + "]\n" + message + "\n");
                conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
            });
        }
        addActivity("ERROR", "Task failed", message, BuddyState.ERROR);
    }

    public void addActivity(String type, String title, String detail, BuddyState state) {
        if (activityModel == null) {
            return;
        }
        String entry = "[" + stamp() + "] " + type + " - " + title + " - " + detail;
        SwingUtilities.invokeLater(() -> {
            activityModel.insertElementAt(entry, 0);
            if (activityModel.size() > 200) {
                activityModel.remove(activityModel.size() - 1);
            }
        });
        activityLogger.log(title, detail);
        currentState = state != null ? state : BuddyState.READY;
    }

    public void setState(BuddyState state) {
        if (state == null) {
            state = BuddyState.READY;
        }
        currentState = state;
        if (statusLabel != null) {
            statusLabel.setText(state.name());
            switch (state) {
                case OFFLINE -> statusLabel.setForeground(new Color(255, 160, 160));
                case LISTENING -> statusLabel.setForeground(new Color(90, 210, 255));
                case THINKING, SEARCHING, EXECUTING -> statusLabel.setForeground(new Color(255, 190, 90));
                case SPEAKING -> statusLabel.setForeground(new Color(180, 140, 255));
                case ERROR -> statusLabel.setForeground(new Color(255, 115, 115));
                default -> statusLabel.setForeground(new Color(90, 220, 150));
            }
        }
        if (coreTitleLabel != null) {
            coreTitleLabel.setText(state.name());
        }
        if (waveformLabel != null) {
            waveformLabel.setText(getWaveformForState(state));
        }
    }

    private String getWaveformForState(BuddyState state) {
        return switch (state) {
            case LISTENING -> "L M H L";
            case THINKING -> "o O o";
            case SEARCHING -> "* O *";
            case EXECUTING -> "! O !";
            case SPEAKING -> "= = = =";
            case ERROR -> "! O !";
            case OFFLINE, READY -> "O";
        };
    }

    public void updateAudioLevel(double level) {
        if (waveformLabel == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (level <= 0.15) {
                waveformLabel.setText("L M H L");
            } else if (level <= 0.35) {
                waveformLabel.setText("L M H M L");
            } else if (level <= 0.6) {
                waveformLabel.setText("M H H M L");
            } else {
                waveformLabel.setText("H H H M L");
            }
        });
    }

    public void markTaskComplete(long durationMs) {
        addActivity("COMPLETE", "Operation finished", "Completed in " + durationMs + " ms", BuddyState.READY);
    }

    public void shutdown() {
        if (clockTimer != null) {
            clockTimer.stop();
        }
        if (systemTimer != null) {
            systemTimer.stop();
        }
        if (frame != null) {
            frame.dispose();
        }
    }

    private void startTimers() {
        clockTimer = new Timer(1000, e -> updateClock());
        systemTimer = new Timer(2000, e -> updateSystemMetrics());
        clockTimer.start();
        systemTimer.start();
        updateClock();
        updateSystemMetrics();
    }

    private void updateClock() {
        if (timeLabel != null) {
            timeLabel.setText(new SimpleDateFormat("hh:mm:ss a").format(new Date()));
        }
    }

    private void updateSystemMetrics() {
        if (cpuLabel != null) {
            double cpu = getCpuLoad();
            if (Double.isFinite(cpu)) {
                cpuLabel.setText(String.format("CPU %.1f%%", cpu * 100.0));
            } else {
                cpuLabel.setText("CPU N/A");
            }
        }
        if (memoryLabel != null) {
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory();
            double ratio = used / (double) runtime.maxMemory();
            memoryLabel.setText(String.format("RAM %.1f%%", ratio * 100.0));
        }
        if (diskLabel != null) {
            long total = getTotalDiskBytes();
            long usable = getUsableDiskBytes();
            if (total > 0 && usable >= 0) {
                diskLabel.setText(String.format("DISK %.1f%%", (1.0 - (usable / (double) total)) * 100.0));
            } else {
                diskLabel.setText("DISK N/A");
            }
        }
    }

    private double getCpuLoad() {
        try {
            OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean) {
                return ((com.sun.management.OperatingSystemMXBean) bean).getCpuLoad();
            }
        } catch (Exception ignored) {
        }
        return Double.NaN;
    }

    private long getTotalDiskBytes() {
        try {
            java.io.File[] roots = java.io.File.listRoots();
            if (roots != null && roots.length > 0) {
                return roots[0].getTotalSpace();
            }
        } catch (Exception ignored) {
        }
        return -1L;
    }

    private long getUsableDiskBytes() {
        try {
            java.io.File[] roots = java.io.File.listRoots();
            if (roots != null && roots.length > 0) {
                return roots[0].getUsableSpace();
            }
        } catch (Exception ignored) {
        }
        return -1L;
    }

    private String loadMemorySummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Stored memory files\n");
        sb.append("-------------------\n");
        for (String fileName : new String[] { "buddy_user_memory.txt", "buddy_websites.txt",
                "buddy_applications.txt" }) {
            Path p = Path.of(fileName);
            if (p.toFile().exists()) {
                sb.append("✓ ").append(fileName).append("\n");
            } else {
                sb.append("! ").append(fileName).append(" (missing)\n");
            }
        }
        sb.append("\n");
        sb.append(memoryManager.loadUserMemory().isEmpty() ? "No user memory stored."
                : memoryManager.loadUserMemory().toString());
        return sb.toString();
    }

    private void handleMemoryAction(String action) {
        try {
            if (action.contains("User")) {
                openFile(Path.of("buddy_user_memory.txt"));
            } else if (action.contains("Web")) {
                openFile(Path.of("buddy_websites.txt"));
            } else if (action.contains("Applications")) {
                openFile(Path.of("buddy_applications.txt"));
            } else {
                showBrainStatus("MEMORY", "Refreshing memory data.");
            }
        } catch (Exception e) {
            showError("Could not open memory file: " + e.getMessage());
        }
    }

    private void handleSettingsAction(String action) {
        if (action.contains("memory")) {
            try {
                openFile(Path.of("buddy_user_memory.txt"));
            } catch (Exception e) {
                showError("Memory file unavailable: " + e.getMessage());
            }
        } else if (action.contains("activity")) {
            try {
                openFile(Path.of("buddy_activity.txt"));
            } catch (Exception e) {
                showError("Activity log unavailable: " + e.getMessage());
            }
        } else if (action.contains("Pause")) {
            setState(BuddyState.READY);
            showBrainStatus("READY", "Listening paused. Resume when ready.");
        } else if (action.contains("Exit")) {
            shutdown();
        }
    }

    private void runFileAction(String action) {
        if (action.contains("Desktop")) {
            showBuddyResponse(commands.process("open desktop"));
        } else if (action.contains("Documents")) {
            showBuddyResponse(commands.process("open documents"));
        } else if (action.contains("Projects")) {
            showBuddyResponse(commands.process("create a folder called Projects"));
        } else if (action.contains("README")) {
            showBuddyResponse(commands.process("open README"));
        }
    }

    private void openFile(Path path) throws IOException {
        if (!path.toFile().exists()) {
            throw new IOException("File not found: " + path);
        }
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(path.toFile());
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }
}