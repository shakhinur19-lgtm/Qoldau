
package kz.qoldau;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONArray;
import org.json.JSONObject;

@WebServlet("/questions")
public class QuestionServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final List<Question> questions =
            Collections.synchronizedList(new ArrayList<Question>());

    private static final AtomicInteger nextId = new AtomicInteger(1);
    private static final AtomicInteger nextAnswerId = new AtomicInteger(1);

    // СҰРАҚТАРДЫ АЛУ
    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        HttpSession session = request.getSession(false);
        String userId = session == null ? null :
                (String) session.getAttribute("userId");

        JSONArray result = new JSONArray();

        synchronized (questions) {
            for (Question q : questions) {

                JSONObject questionJson = new JSONObject();

                questionJson.put("id", q.id);
                questionJson.put("category", q.category);
                questionJson.put("title", q.title);
                questionJson.put("content", q.content);
                questionJson.put("isOwner",
                        userId != null && userId.equals(q.ownerId));

                JSONArray answersJson = new JSONArray();

                synchronized (q.answers) {
                    for (Answer a : q.answers) {

                        JSONObject answerJson = new JSONObject();

                        answerJson.put("id", a.id);
                        answerJson.put("text", a.text);
                        answerJson.put("helpful", a.helpful);
                        answerJson.put("author", "Анонимді студент");

                        answersJson.put(answerJson);
                    }
                }

                questionJson.put("answers", answersJson);
                result.put(questionJson);
            }
        }

        response.getWriter().write(result.toString());
    }

    // СҰРАҚ ҚОСУ, ЖАУАП ҚОСУ ЖӘНЕ ПАЙДАЛЫ ДАУЫС БЕРУ
    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response) throws IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        HttpSession session = request.getSession(false);
        String userId = session == null ? null :
                (String) session.getAttribute("userId");

        if (userId == null) {
            sendError(response, 401, "Алдымен жүйеге кіріңіз.");
            return;
        }

        String action = request.getParameter("action");

        // ЖАУАПҚА ПАЙДАЛЫ ДАУЫС БЕРУ
        if ("helpful".equals(action)) {

            int questionId;
            int answerId;

            try {
                questionId = Integer.parseInt(
                        request.getParameter("questionId"));
                answerId = Integer.parseInt(
                        request.getParameter("answerId"));
            } catch (Exception e) {
                sendError(response, 400, "Сұрақ немесе жауап ID дұрыс емес.");
                return;
            }

            synchronized (questions) {
                for (Question q : questions) {

                    if (q.id == questionId) {

                        synchronized (q.answers) {
                            for (Answer a : q.answers) {

                                if (a.id == answerId) {

                                    synchronized (a.votedUsers) {

                                        if (a.votedUsers.contains(userId)) {
                                            sendError(response, 409,
                                                    "Сіз бұл жауапқа дауыс беріп қойғансыз.");
                                            return;
                                        }

                                        a.votedUsers.add(userId);
                                        a.helpful++;
                                    }

                                    JSONObject result = new JSONObject();
                                    result.put("success", true);
                                    result.put("helpful", a.helpful);

                                    response.getWriter().write(result.toString());
                                    return;
                                }
                            }
                        }

                        sendError(response, 404, "Жауап табылмады.");
                        return;
                    }
                }
            }

            sendError(response, 404, "Сұрақ табылмады.");
            return;
        }

        // ЖАУАП ҚОСУ
        if ("answer".equals(action)) {

            int questionId;

            try {
                questionId = Integer.parseInt(
                        request.getParameter("questionId"));
            } catch (Exception e) {
                sendError(response, 400, "Сұрақ ID дұрыс емес.");
                return;
            }

            String text = request.getParameter("text");

            if (text == null || text.trim().isEmpty()) {
                sendError(response, 400, "Жауап мәтінін енгізіңіз.");
                return;
            }

            synchronized (questions) {
                for (Question q : questions) {

                    if (q.id == questionId) {

                        Answer answer = new Answer(
                                nextAnswerId.getAndIncrement(),
                                text.trim(),
                                userId
                        );

                        synchronized (q.answers) {
                            q.answers.add(answer);
                        }

                        JSONObject result = new JSONObject();
                        result.put("success", true);
                        result.put("message", "Жауап қосылды.");

                        response.getWriter().write(result.toString());
                        return;
                    }
                }
            }

            sendError(response, 404, "Сұрақ табылмады.");
            return;
        }

        // ЖАҢА СҰРАҚ ҚОСУ
        String category = request.getParameter("category");
        String title = request.getParameter("title");
        String content = request.getParameter("content");

        if (category == null || title == null || content == null ||
                category.trim().isEmpty() ||
                title.trim().isEmpty() ||
                content.trim().isEmpty()) {

            sendError(response, 400, "Барлық өрістерді толтырыңыз.");
            return;
        }

        Question question = new Question(
                nextId.getAndIncrement(),
                category.trim(),
                title.trim(),
                content.trim(),
                userId
        );

        questions.add(question);

        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("id", question.id);
        result.put("message", "Сұрақ сәтті жарияланды.");

        response.getWriter().write(result.toString());
    }

    // СҰРАҚТЫ ӨҢДЕУ
    @Override
    protected void doPut(HttpServletRequest request,
                         HttpServletResponse response) throws IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        HttpSession session = request.getSession(false);
        String userId = session == null ? null :
                (String) session.getAttribute("userId");

        if (userId == null) {
            sendError(response, 401, "Алдымен жүйеге кіріңіз.");
            return;
        }

        String body = readBody(request);
        String form = URLDecoder.decode(body, "UTF-8");

        String category = getFormValue(form, "category");
        String title = getFormValue(form, "title");
        String content = getFormValue(form, "content");

        int questionId;

        try {
            questionId = Integer.parseInt(
                    getFormValue(form, "id"));
        } catch (Exception e) {
            sendError(response, 400, "Сұрақ ID дұрыс емес.");
            return;
        }

        if (category == null || title == null || content == null ||
                category.trim().isEmpty() ||
                title.trim().isEmpty() ||
                content.trim().isEmpty()) {

            sendError(response, 400, "Барлық өрістерді толтырыңыз.");
            return;
        }

        synchronized (questions) {
            for (Question q : questions) {

                if (q.id == questionId) {

                    if (!q.ownerId.equals(userId)) {
                        sendError(response, 403,
                                "Бұл сұрақты тек авторы өңдей алады.");
                        return;
                    }

                    q.category = category.trim();
                    q.title = title.trim();
                    q.content = content.trim();

                    JSONObject result = new JSONObject();
                    result.put("success", true);
                    result.put("message", "Сұрақ өңделді.");

                    response.getWriter().write(result.toString());
                    return;
                }
            }
        }

        sendError(response, 404, "Сұрақ табылмады.");
    }

    // СҰРАҚТЫ ӨШІРУ
    @Override
    protected void doDelete(HttpServletRequest request,
                            HttpServletResponse response) throws IOException {

        response.setContentType("application/json;charset=UTF-8");

        HttpSession session = request.getSession(false);
        String userId = session == null ? null :
                (String) session.getAttribute("userId");

        if (userId == null) {
            sendError(response, 401, "Алдымен жүйеге кіріңіз.");
            return;
        }

        int questionId;

        try {
            questionId = Integer.parseInt(
                    request.getParameter("id"));
        } catch (Exception e) {
            sendError(response, 400, "Сұрақ ID дұрыс емес.");
            return;
        }

        synchronized (questions) {
            for (Question q : questions) {

                if (q.id == questionId) {

                    if (!q.ownerId.equals(userId)) {
                        sendError(response, 403,
                                "Бұл сұрақты тек авторы өшіре алады.");
                        return;
                    }

                    questions.remove(q);

                    JSONObject result = new JSONObject();
                    result.put("success", true);
                    result.put("message", "Сұрақ өшірілді.");

                    response.getWriter().write(result.toString());
                    return;
                }
            }
        }

        sendError(response, 404, "Сұрақ табылмады.");
    }

    // REQUEST BODY ОҚУ
    private String readBody(HttpServletRequest request) throws IOException {

        StringBuilder builder = new StringBuilder();

        try (BufferedReader reader = request.getReader()) {
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }

        return builder.toString();
    }

    // FORM МӘНІН АЛУ
    private String getFormValue(String form, String key)
            throws IOException {

        String[] pairs = form.split("&");

        for (String pair : pairs) {

            String[] parts = pair.split("=", 2);

            if (parts.length == 2 &&
                    parts[0].equals(key)) {

                return URLDecoder.decode(
                        parts[1], "UTF-8");
            }
        }

        return null;
    }

    // ҚАТЕ ЖІБЕРУ
    private void sendError(HttpServletResponse response,
                           int status,
                           String message) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        JSONObject result = new JSONObject();
        result.put("success", false);
        result.put("message", message);

        response.getWriter().write(result.toString());
    }

    // СҰРАҚ КЛАСЫ
    private static class Question {

        int id;
        String category;
        String title;
        String content;
        String ownerId;

        List<Answer> answers =
                Collections.synchronizedList(new ArrayList<Answer>());

        Question(int id, String category, String title,
                 String content, String ownerId) {

            this.id = id;
            this.category = category;
            this.title = title;
            this.content = content;
            this.ownerId = ownerId;
        }
    }

    // ЖАУАП КЛАСЫ
    private static class Answer {

        int id;
        String text;
        String ownerId;
        int helpful = 0;

        Set<String> votedUsers =
                Collections.synchronizedSet(new HashSet<String>());

        Answer(int id, String text, String ownerId) {

            this.id = id;
            this.text = text;
            this.ownerId = ownerId;
        }
    }
}
