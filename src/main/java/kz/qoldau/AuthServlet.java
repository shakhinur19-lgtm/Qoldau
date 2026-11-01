
package kz.qoldau;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/auth")
public class AuthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Map<String, User> users =
            new ConcurrentHashMap<>();

    private static final SecureRandom RANDOM =
            new SecureRandom();


private static class User {
    String salt;
    String passwordHash;

    User(String salt, String passwordHash) {
        this.salt = salt;
        this.passwordHash = passwordHash;
    }
}

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");

        HttpSession session = request.getSession(false);

        if (session != null
                && session.getAttribute("userId") != null) {

            String username =
                    (String) session.getAttribute("userId");

            response.getWriter().write(
                "{\"loggedIn\":true,\"username\":\""
                + username + "\"}"
            );

        } else {
            response.getWriter().write(
                "{\"loggedIn\":false}"
            );
        }
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        String action = request.getParameter("action");
        String username = request.getParameter("username");
        String password = request.getParameter("password");

        if ("logout".equals(action)) {
            HttpSession session = request.getSession(false);

            if (session != null) {
                session.invalidate();
            }

            response.getWriter().write(
                "{\"success\":true,\"message\":\"Жүйеден шықтыңыз.\"}"
            );
            return;
        }

        if (username == null || password == null) {
            sendError(response, 400,
                    "Логин мен құпиясөзді енгізіңіз.");
            return;
        }

        username = username.trim().toLowerCase();

        if (!username.matches("[a-z0-9_]{3,20}")) {
            sendError(response, 400,
                    "Логин 3–20 таңбадан тұруы керек. "
                    + "Латын әріптерін, сандарды және _ таңбасын қолданыңыз.");
            return;
        }

        if (password.length() < 8 || password.length() > 128) {
            sendError(response, 400,
                    "Құпиясөз 8–128 таңбадан тұруы керек.");
            return;
        }

        if ("register".equals(action)) {

            byte[] saltBytes = new byte[16];
            RANDOM.nextBytes(saltBytes);

            String salt = Base64.getEncoder()
                    .encodeToString(saltBytes);

            String hash;

            try {
                hash = hashPassword(password, saltBytes);
            } catch (Exception e) {
                sendError(response, 500,
                        "Құпиясөзді өңдеу кезінде қате шықты.");
                return;
            }

            User newUser = new User(salt, hash);

            if (users.putIfAbsent(username, newUser) != null) {
                sendError(response, 409,
                        "Бұл логин тіркелген. Басқа логин таңдаңыз.");
                return;
            }

            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }

            HttpSession session = request.getSession(true);
            session.setAttribute("userId", username);

            response.getWriter().write(
                "{\"success\":true,\"message\":\"Тіркелу сәтті аяқталды.\"}"
            );
            return;
        }

        if ("login".equals(action)) {

            User user = users.get(username);

            if (user == null) {
                sendError(response, 401,
                        "Логин немесе құпиясөз қате.");
                return;
            }

            try {
                byte[] saltBytes = Base64.getDecoder()
                        .decode(user.salt);

                String enteredHash =
                        hashPassword(password, saltBytes);

                boolean matches = MessageDigest.isEqual(
                    enteredHash.getBytes("UTF-8"),
                    user.passwordHash.getBytes("UTF-8")
                );

                if (!matches) {
                    sendError(response, 401,
                            "Логин немесе құпиясөз қате.");
                    return;
                }

                HttpSession oldSession = request.getSession(false);
                if (oldSession != null) {
                    oldSession.invalidate();
                }

                HttpSession session = request.getSession(true);
                session.setAttribute("userId", username);

                response.getWriter().write(
                    "{\"success\":true,\"message\":\"Жүйеге сәтті кірдіңіз.\"}"
                );

            } catch (Exception e) {
                sendError(response, 500,
                        "Жүйеге кіру кезінде қате шықты.");
            }

            return;
        }

        sendError(response, 400, "Белгісіз әрекет.");
    }

    private static String hashPassword(String password, byte[] salt)
            throws Exception {

        KeySpec spec = new PBEKeySpec(
            password.toCharArray(), salt, 120000, 256
        );

        SecretKeyFactory factory =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        byte[] hash = factory.generateSecret(spec).getEncoded();

        return Base64.getEncoder().encodeToString(hash);
    }

    private void sendError(HttpServletResponse response,
                           int status, String message)
            throws IOException {

        response.setStatus(status);

        String safeMessage = message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");

        response.getWriter().write(
            "{\"success\":false,\"message\":\""
            + safeMessage + "\"}"
        );
    }
}

