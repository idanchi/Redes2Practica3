package com.mycompany.chat;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;

public class ChatPrivado extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    private PrintWriter out;
    private String usuarioLocal;
    private String usuarioRemoto;

    public ChatPrivado(String usuarioLocal, String usuarioRemoto, PrintWriter out) {
        this.usuarioLocal = usuarioLocal;
        this.usuarioRemoto = usuarioRemoto;
        this.out = out;

        setTitle("Chat privado con " + usuarioRemoto);
        setSize(400, 400);
        setLocationRelativeTo(null);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        inputField = new JTextField();
        inputField.addActionListener(e -> enviarMensaje());

        // Panel para botones enviar y enviar archivo
        JPanel panelMensaje = new JPanel(new BorderLayout());

        JPanel panelBotonesEnvio = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        JButton btnEnviar = new JButton("Enviar");
        btnEnviar.addActionListener(e -> enviarMensaje());

        JButton btnEnviarArchivo = new JButton();
        btnEnviarArchivo.setToolTipText("Enviar archivo");
        try {
            ImageIcon iconClip = new ImageIcon(getClass().getResource("/images/clip.png"));
            btnEnviarArchivo.setIcon(iconClip);
        } catch (Exception e) {
            btnEnviarArchivo.setText("📎");
        }
        btnEnviarArchivo.setPreferredSize(new Dimension(30, 30));

        // Acción aún no implementada, sólo placeholder
        btnEnviarArchivo.addActionListener(e ->
            JOptionPane.showMessageDialog(this, "Función enviar archivo aún no implementada"));

        panelBotonesEnvio.add(btnEnviarArchivo);
        panelBotonesEnvio.add(btnEnviar);

        panelMensaje.add(inputField, BorderLayout.CENTER);
        panelMensaje.add(panelBotonesEnvio, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(panelMensaje, BorderLayout.SOUTH);
    }

    private void enviarMensaje() {
        String msg = inputField.getText().trim();
        if (!msg.isEmpty()) {
            out.println("/privado " + usuarioRemoto + " " + msg);
            appendChat("Yo: " + msg);
            inputField.setText("");
        }
    }

    public void recibirMensaje(String remitente, String mensaje) {
        appendChat("[" + remitente + "]: " + mensaje);
    }

    private void appendChat(String msg) {
        chatArea.append(msg + "\n");
    }
}