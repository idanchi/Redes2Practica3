package com.mycompany.chat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;

public class ChatCliente extends JFrame {

    private JTextArea areaChat;
    private JTextField campoMensaje;
    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;
    private String nombre;

    public ChatCliente() {
        setTitle("Chat Cliente");
        setSize(600, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        areaChat = new JTextArea();
        areaChat.setEditable(false);
        add(new JScrollPane(areaChat), BorderLayout.CENTER);

        JPanel panelAbajo = new JPanel(new BorderLayout());
        campoMensaje = new JTextField();
        campoMensaje.addActionListener(e -> enviarMensaje());
        panelAbajo.add(campoMensaje, BorderLayout.CENTER);

        JButton btnEnviar = new JButton("Enviar");
        btnEnviar.addActionListener(e -> enviarMensaje());
        panelAbajo.add(btnEnviar, BorderLayout.EAST);

        add(panelAbajo, BorderLayout.SOUTH);

        JPanel panelBotones = new JPanel(new GridLayout(1, 4));

        JButton btnSala = new JButton("Cambiar Sala");
        btnSala.addActionListener(e -> cambiarSala());
        panelBotones.add(btnSala);

        JButton btnListarSalas = new JButton("Listar Salas");
        btnListarSalas.addActionListener(e -> out.println("/salas"));
        panelBotones.add(btnListarSalas);

        JButton btnPrivado = new JButton("Privado");
        btnPrivado.addActionListener(e -> mensajePrivado());
        panelBotones.add(btnPrivado);

        JButton btnArchivo = new JButton("Enviar Archivo");
        btnArchivo.addActionListener(e -> enviarArchivo());
        panelBotones.add(btnArchivo);

        add(panelBotones, BorderLayout.NORTH);

        conectar();
    }

    private void conectar() {
        try {
            socket = new Socket("localhost", 5000);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            Thread lector = new Thread(() -> {
                try {
                    String msg;
                    while ((msg = in.readLine()) != null) {

                        // ============================
                        //  PROTOCOLO DE LOGIN
                        // ============================
                        if (msg.equals("INGRESA_TU_NOMBRE")) {
                            nombre = JOptionPane.showInputDialog(this, "Ingresa tu nombre:");
                            if (nombre == null || nombre.trim().isEmpty()) nombre = "Usuario";
                            out.println(nombre);
                            continue;
                        }

                        // ============================
                        //  LISTA DE SALAS
                        // ============================
                        if (msg.startsWith("SALAS ")) {
                            String lista = msg.substring(6);
                            String[] salas = lista.isEmpty() ? new String[0] : lista.split(",");

                            if (salas.length == 0) {
                                JOptionPane.showMessageDialog(this, "No hay salas disponibles.");
                            } else {
                                String sel = (String) JOptionPane.showInputDialog(
                                        this, "Selecciona una sala:",
                                        "Salas", JOptionPane.PLAIN_MESSAGE,
                                        null, salas, salas[0]
                                );
                                if (sel != null) out.println("/sala " + sel);
                            }
                            continue;
                        }

                        // ============================
                        //  RECIBIR ARCHIVO
                        // ============================
                        if (msg.startsWith("/incomingFile ")) {
                            recibirArchivo(msg);
                            continue;
                        }

                        // ============================
                        // MENSAJE NORMAL
                        // ============================
                        append(msg);
                    }
                } catch (Exception e) {
                    append("Desconectado del servidor.");
                }
            });
            lector.start();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "No se pudo conectar al servidor.");
            System.exit(0);
        }
    }

    private void append(String msg) {
        SwingUtilities.invokeLater(() -> areaChat.append(msg + "\n"));
    }

    private void enviarMensaje() {
        String msg = campoMensaje.getText().trim();
        if (msg.isEmpty()) return;

        out.println(msg);
        campoMensaje.setText("");
    }

    private void cambiarSala() {
        String sala = JOptionPane.showInputDialog(this, "Nombre de sala:");
        if (sala != null && !sala.trim().isEmpty()) {
            out.println("/sala " + sala.trim());
        }
    }

    private void mensajePrivado() {
        String dest = JOptionPane.showInputDialog(this, "Enviar a:");
        if (dest == null || dest.trim().isEmpty()) return;

        String txt = JOptionPane.showInputDialog(this, "Mensaje:");
        if (txt == null || txt.trim().isEmpty()) return;

        out.println("/priv " + dest + " " + txt);
    }

    // =========================================================
    // ENVIAR ARCHIVO
    // =========================================================
    private void enviarArchivo() {
        JFileChooser chooser = new JFileChooser();
        int r = chooser.showOpenDialog(this);

        if (r != JFileChooser.APPROVE_OPTION) return;

        File f = chooser.getSelectedFile();
        String nombreArchivo = f.getName();
        long size = f.length();

        String destino = JOptionPane.showInputDialog(this, "Enviar archivo a:");
        if (destino == null || destino.trim().isEmpty()) return;

        try {
            // Avisar al servidor
            out.println("/file " + destino + " " + nombreArchivo + " " + size);

            // Enviar bytes
            FileInputStream fis = new FileInputStream(f);
            OutputStream os = socket.getOutputStream();

            byte[] buffer = new byte[4096];
            int le;

            while ((le = fis.read(buffer)) != -1) {
                os.write(buffer, 0, le);
            }

            os.flush();
            fis.close();

            append("Archivo enviado a " + destino + ": " + nombreArchivo);

        } catch (Exception ex) {
            append("Error enviando archivo: " + ex.getMessage());
        }
    }

    // =========================================================
    // RECIBIR ARCHIVO
    // =========================================================
    private void recibirArchivo(String msg) {
        try {
            String[] p = msg.split(" ");
            String remitente = p[1];
            String nombreArchivo = p[2];
            long size = Long.parseLong(p[3]);

            // Elegir dónde guardar
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File(nombreArchivo));
            int r = chooser.showSaveDialog(this);

            InputStream is = socket.getInputStream();

            if (r != JFileChooser.APPROVE_OPTION) {
                // Consumir bytes igualmente
                byte[] basura = new byte[4096];
                long restante = size;
                while (restante > 0) {
                    int le = is.read(basura, 0, (int) Math.min(basura.length, restante));
                    if (le <= 0) break;
                    restante -= le;
                }
                append("Archivo rechazado: " + nombreArchivo);
                return;
            }

            File f = chooser.getSelectedFile();
            FileOutputStream fos = new FileOutputStream(f);

            byte[] buffer = new byte[4096];
            long restante = size;

            while (restante > 0) {
                int le = is.read(buffer, 0, (int) Math.min(buffer.length, restante));
                if (le == -1) break;

                fos.write(buffer, 0, le);
                restante -= le;
            }

            fos.close();
            append("Archivo recibido de " + remitente + ": " + f.getAbsolutePath());

        } catch (Exception e) {
            append("Error recibiendo archivo: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatCliente().setVisible(true));
    }
}
