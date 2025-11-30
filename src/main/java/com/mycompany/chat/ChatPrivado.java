package com.mycompany.chat;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;

public class ChatPrivado extends JFrame {

    private JTextArea chatArea;
    private JTextField inputField;
    private PrintWriter out;
    private String yo;
    private String destino;

    public ChatPrivado(String yo, String destino, PrintWriter out) {
        this.yo = yo;
        this.destino = destino;
        this.out = out;

        setTitle("Privado con " + destino);
        setSize(350, 400);

        chatArea = new JTextArea();
        chatArea.setEditable(false);

        inputField = new JTextField();
        inputField.addActionListener(e -> enviar());

        add(new JScrollPane(chatArea), BorderLayout.CENTER);
        add(inputField, BorderLayout.SOUTH);
    }

    private void enviar() {
        String msg = inputField.getText().trim();
        if (msg.isEmpty()) return;

        out.println("/privado " + destino + " " + msg);
        chatArea.append("Yo: " + msg + "\n");
        inputField.setText("");
    }

    public void recibirMensaje(String remitente, String texto) {
        chatArea.append(remitente + ": " + texto + "\n");
    }
}
