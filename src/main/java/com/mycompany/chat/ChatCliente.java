package com.mycompany.chat;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class ChatCliente extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    private PrintWriter out;
    private String nombreUsuario;
    private Map<String, ChatPrivado> chatsPrivados;
    private String salaActiva = null; // para controlar sala actual
    

    public ChatCliente() {
        // Primero pedir el nombre de usuario
        nombreUsuario = JOptionPane.showInputDialog("Ingrese su nombre de usuario:");
        if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Debe ingresar un nombre de usuario válido");
            System.exit(0);
        }

        setTitle("Cliente de Chat - " + nombreUsuario);
        setSize(600, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
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
            // Carga un icono clip pequeño 
            ImageIcon iconClip = new ImageIcon(getClass().getResource("/images/clip.png"));
            btnEnviarArchivo.setIcon(iconClip);
        } catch (Exception e) {
            // fallback: emoji clip
            btnEnviarArchivo.setText("📎");
        }
        btnEnviarArchivo.setPreferredSize(new Dimension(30, 30));

        // Aquí puedes agregar acción para enviar archivo (más adelante)
        btnEnviarArchivo.addActionListener(e -> JOptionPane.showMessageDialog(this, "Función enviar archivo aún no implementada"));

        panelBotonesEnvio.add(btnEnviarArchivo);
        panelBotonesEnvio.add(btnEnviar);

        panelMensaje.add(inputField, BorderLayout.CENTER);
        panelMensaje.add(panelBotonesEnvio, BorderLayout.EAST);

        JPanel botones = new JPanel(new GridLayout(2, 3, 5, 5));
        JButton btnCrear = new JButton("Crear sala");
        JButton btnListar = new JButton("Listar salas");
        JButton btnIngresar = new JButton("Ingresar sala");
        JButton btnListarUsuarios = new JButton("Listar usuarios");
        JButton btnMensajePrivado = new JButton("Mensaje privado");
        JButton btnSalir = new JButton("Salir");

        botones.add(btnCrear);
        botones.add(btnListar);
        botones.add(btnIngresar);
        botones.add(btnListarUsuarios);
        botones.add(btnMensajePrivado);
        botones.add(btnSalir);

        chatsPrivados = new HashMap<>();

        // Eventos botones
        btnCrear.addActionListener(e -> {
            String sala = JOptionPane.showInputDialog("Nombre de la nueva sala:");
            if (sala != null && !sala.trim().isEmpty()) {
                //out.println("/crear " + sala);
                // Auto ingresar a la sala creada
                salaActiva = sala;
                chatArea.setText(""); // Limpiar chat al cambiar sala
                appendChat("Creada e ingresada a sala: " + sala);
            }
        });

        btnListar.addActionListener(e -> {
            // Mostrar ventana con lista interactiva de salas (simulación)
            String[] salas = obtenerSalas(); // Método simulado para obtener salas
            if (salas.length == 0) {
                JOptionPane.showMessageDialog(this, "No hay salas disponibles");
                return;
            }
            String seleccion = (String) JOptionPane.showInputDialog(this, "Seleccione sala:",
                    "Lista de salas", JOptionPane.PLAIN_MESSAGE, null, salas, salas[0]);
            if (seleccion != null) {
                salaActiva = seleccion;
                chatArea.setText("");
                appendChat("Ingresaste a la sala: " + seleccion);
                //out.println("/ingresar " + seleccion);
            }
        });

        btnIngresar.addActionListener(e -> {
            String sala = JOptionPane.showInputDialog("Nombre de la sala:");
            if (sala != null && !sala.trim().isEmpty()) {
                salaActiva = sala;
                chatArea.setText("");
                appendChat("Ingresaste a la sala: " + sala);
                //out.println("/ingresar " + sala);
            }
        });

        btnListarUsuarios.addActionListener(e -> out.println("/usuarios"));

        btnMensajePrivado.addActionListener(e -> {
            String usuario = JOptionPane.showInputDialog("Nombre del usuario:");
            if (usuario != null && !usuario.trim().isEmpty()) {
                abrirChatPrivado(usuario);
            }
        });

        btnSalir.addActionListener(e -> {
            //out.println("/salir");
            System.exit(0);
        });

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(panelMensaje, BorderLayout.SOUTH);
        add(botones, BorderLayout.NORTH);

        conectar();
    }

    private void enviarMensaje() {
        String msg = inputField.getText().trim();
        if (!msg.isEmpty() && salaActiva != null) {
            if (!msg.startsWith("/")) {
                out.println(msg);
                appendChat("Yo: " + msg);  // Mostrar localmente como "Yo: mensaje"
            } else {
                out.println(msg); // comandos no se muestran
            }
            inputField.setText("");
        } else if (salaActiva == null) {
            JOptionPane.showMessageDialog(this, "Debe ingresar a una sala primero.");
        }
    }

    private void appendChat(String msg) {
        chatArea.append(msg + "\n");
    }

    private void conectar() {
        try {
            Socket socket = new Socket("localhost", 12345);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Enviar el nombre de usuario al servidor
            out.println(nombreUsuario);

            // Lee mensajes en otro hilo
            new Thread(() -> {
                String msg;
                try {
                    while ((msg = in.readLine()) != null) {
                        if (msg.startsWith("[Privado] ")) {
                            // Manejar mensajes privados
                            int inicio = msg.indexOf(" ") + 1;
                            int separador = msg.indexOf(":", inicio);
                            if (separador > inicio) {
                                String remitente = msg.substring(inicio, separador).trim();
                                String mensajePrivado = msg.substring(separador + 1).trim();
                                abrirChatPrivado(remitente);
                                chatsPrivados.get(remitente).recibirMensaje(remitente, mensajePrivado);
                            }
                        } else {
                            // Evitar mostrar mensajes propios para evitar duplicados
                            if (!msg.startsWith(nombreUsuario + ": ")) {
                                appendChat(msg);
                            }
                        }
                    }
                } catch (IOException e) {
                    appendChat("Desconectado del servidor.");
                }
            }).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "No se pudo conectar al servidor: " + e.getMessage());
            System.exit(1);
        }
    }

    private void abrirChatPrivado(String usuario) {
        if (!chatsPrivados.containsKey(usuario)) {
            ChatPrivado chatPrivado = new ChatPrivado(nombreUsuario, usuario, out);
            chatsPrivados.put(usuario, chatPrivado);
            chatPrivado.setVisible(true);
        } else {
            chatsPrivados.get(usuario).setVisible(true);
            chatsPrivados.get(usuario).toFront();
        }
    }

    // Simulación de obtención de salas (en la práctica, pedirlas al servidor)
    private String[] obtenerSalas() {
        // Puedes modificar para pedir salas reales al servidor y almacenarlas
        // Por ahora, retornamos un arreglo estático para demo
        return new String[]{"SalaPrueba1", "SalaPrueba2", "SalaPrueba3"};
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ChatCliente().setVisible(true);
        });
    }
}
