package com.mycompany.chat;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class ChatCliente extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    private PrintWriter out;
    private String nombreUsuario;
    private Map<String, ChatPrivado> chatsPrivados;
    private String salaActiva = null;

    public ChatCliente() {

        nombreUsuario = JOptionPane.showInputDialog("Ingrese su nombre de usuario:");
        if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
            System.exit(0);
        }

        setTitle("Chat - " + nombreUsuario);
        setSize(600, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        chatArea = new JTextArea();
        chatArea.setEditable(false);

        inputField = new JTextField();
        inputField.addActionListener(e -> enviarMensaje());

        chatsPrivados = new HashMap<>();

        JButton btnListUsuarios = new JButton("Usuarios");
        btnListUsuarios.addActionListener(e -> out.println("/usuarios"));

        JButton btnPrivado = new JButton("Privado");
        btnPrivado.addActionListener(e -> listarUsuariosParaPrivado());

        JButton btnCrear = new JButton("Crear sala");
        btnCrear.addActionListener(e -> crearSala());

        JButton btnListar = new JButton("Listar salas");
        btnListar.addActionListener(e -> out.println("/salas"));

        JButton btnIngresar = new JButton("Ingresar sala");
        btnIngresar.addActionListener(e -> ingresarSalaManual());

        JPanel botones = new JPanel();
        botones.add(btnCrear);
        botones.add(btnListar);
        botones.add(btnIngresar);
        botones.add(btnListUsuarios);
        botones.add(btnPrivado);

        add(new JScrollPane(chatArea), BorderLayout.CENTER);
        add(inputField, BorderLayout.SOUTH);
        add(botones, BorderLayout.NORTH);

        conectar();
    }

    private void crearSala() {
        String sala = JOptionPane.showInputDialog("Nombre sala:");
        if (sala != null && !sala.trim().isEmpty()) {
            out.println("/crear " + sala);
        }
    }

    private void ingresarSalaManual() {
        String sala = JOptionPane.showInputDialog("Sala:");
        if (sala != null && !sala.trim().isEmpty()) {
            out.println("/ingresar " + sala);
        }
    }

    private void listarUsuariosParaPrivado() {
        out.println("/usuarios");
    }

    private void enviarMensaje() {
        String msg = inputField.getText().trim();
        if (msg.isEmpty()) return;

        out.println(msg);
        // REMOVED: append("Yo: " + msg);
        inputField.setText("");
    }

    private void append(String t) {
        chatArea.append(t + "\n");
    }

    private void conectar() {
        try {
            Socket socket = new Socket("localhost", 12345);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println(nombreUsuario);

            new Thread(() -> {
                try {
                    String msg;
                    while ((msg = in.readLine()) != null) {
                        if (msg.equals("INGRESA_TU_NOMBRE")) continue;

                        if (msg.startsWith("SALAS ")) {
                            mostrarSalas(msg.substring(6));
                            continue;
                        }

                        if (msg.startsWith("USUARIOS ")) {
                            mostrarUsuariosParaPrivado(msg.substring(9));
                            continue;
                        }

                        if (msg.startsWith("[Privado]")) {
                            procesarPrivado(msg);
                            continue;
                        }

                        append(msg);
                    }
                } catch (Exception e) {
                    append("Desconectado.");
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "No se pudo conectar");
            System.exit(0);
        }
    }

    private void procesarPrivado(String msg) {
        int ini = msg.indexOf(" ") + 1;
        int sep = msg.indexOf(":", ini);
        String remitente = msg.substring(ini, sep).trim();
        String contenido = msg.substring(sep + 1).trim();

        abrirChatPrivado(remitente);
        chatsPrivados.get(remitente).recibirMensaje(remitente, contenido);
    }

    private void mostrarUsuariosParaPrivado(String lista) {
        String[] users = lista.split(",");
        if (users.length == 0) {
            JOptionPane.showMessageDialog(this, "No hay usuarios");
            return;
        }

        String u = (String) JOptionPane.showInputDialog(
                this, "Elige usuario:", "Chat privado",
                JOptionPane.PLAIN_MESSAGE, null, users, users[0]);

        if (u != null) abrirChatPrivado(u);
    }

    private void mostrarSalas(String lista) {
        String[] salas = lista.split(",");
        if (salas.length == 0) {
            JOptionPane.showMessageDialog(this, "No hay salas");
            return;
        }

        String sala = (String) JOptionPane.showInputDialog(
                this, "Elige sala:", "Salas",
                JOptionPane.PLAIN_MESSAGE, null, salas, salas[0]);

        if (sala != null) out.println("/ingresar " + sala);
    }

    private void abrirChatPrivado(String usuario) {
        if (!chatsPrivados.containsKey(usuario)) {
            ChatPrivado cp = new ChatPrivado(nombreUsuario, usuario, out);
            chatsPrivados.put(usuario, cp);
            cp.setVisible(true);
        } else {
            chatsPrivados.get(usuario).setVisible(true);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatCliente().setVisible(true));
    }
}
