import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class WebServer {

    private Hotel hotel;
    private int port;
    private String adminEmail;
    private String adminPassword;

    private String adminToken = null;

    private String[] guestTokens = new String[200];
    private String[] guestEmails = new String[200];
    private int guestNext = 0;

    private User currentUser = null;
    private boolean currentIsAdmin = false;
    private String currentGuestToken = "";
    private String pendingCookie = null;

    public WebServer(Hotel hotel, int port, String adminEmail, String adminPassword) {
        this.hotel = hotel;
        this.port = port;
        this.adminEmail = adminEmail.toLowerCase();
        this.adminPassword = adminPassword;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/favicon.ico")) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }

        boolean isPost = ex.getRequestMethod().equals("POST");
        String data = ex.getRequestURI().getRawQuery();
        if (data == null) {
            data = "";
        }
        if (isPost) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            data = data + "&" + body;
        }

        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        String adminCookie = cookieValue(cookie, "admin_session");
        String guestCookie = cookieValue(cookie, "guest_session");

        String html;
        String cookieToSet;
        synchronized (hotel) {
            currentIsAdmin = adminToken != null && adminCookie.equals(adminToken);
            currentGuestToken = guestCookie;
            currentUser = findSessionUser(guestCookie);
            pendingCookie = null;
            html = route(path, data, isPost);
            cookieToSet = pendingCookie;
        }

        if (cookieToSet != null) {
            ex.getResponseHeaders().add("Set-Cookie", cookieToSet);
        }
        if (html.startsWith("REDIRECT:")) {
            ex.getResponseHeaders().set("Location", html.substring(9));
            ex.sendResponseHeaders(303, -1);
            ex.close();
            return;
        }

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        OutputStream os = ex.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private String route(String path, String data, boolean isPost) {
        if (path.startsWith("/admin")) {
            return routeAdmin(path, data, isPost);
        }
        switch (path) {
            case "/":
                return pageHome(data);
            case "/search":
                return pageSearch(data);
            case "/room":
                return pageRoom(data);
            case "/calc":
                return pageCalc(data);
            case "/login":
                if (isPost) {
                    return doUserLogin(data);
                }
                if (currentIsAdmin) {
                    return "REDIRECT:/admin";
                }
                return currentUser != null ? "REDIRECT:/my" : pageUserLogin("", param(data, "next"), "");
            case "/register":
                return isPost ? doRegister(data) : pageRegister("", param(data, "next"), "", "", "");
            case "/logout":
                removeGuestSession(currentGuestToken);
                pendingCookie = "guest_session=; Path=/; Max-Age=0";
                return "REDIRECT:/";
            case "/book":
                if (currentUser == null && currentIsAdmin) {
                    return shell("Бронювання", "<div class='box err'>Ви увійшли як адміністратор. Бронюють номери гості зі своїх акаунтів. "
                            + "<a href='/admin'>До панелі адміністратора</a></div>", false);
                }
                if (currentUser == null) {
                    return "REDIRECT:/login?next=" + enc(isPost ? "/search" : "/book?" + data);
                }
                return isPost ? doBook(data) : pageBookForm(data);
            case "/my":
                if (currentUser == null && currentIsAdmin) {
                    return "REDIRECT:/admin";
                }
                if (currentUser == null) {
                    return "REDIRECT:/login?next=" + enc("/my");
                }
                return pageMy("", false);
            case "/my/cancel":
                if (currentUser == null) {
                    return "REDIRECT:/login?next=" + enc("/my");
                }
                return isPost ? doMyCancel(data) : "REDIRECT:/my";
            default:
                return shell("Не знайдено", "<div class='box err'>Сторінку не знайдено.</div>", false);
        }
    }

    private String routeAdmin(String path, String data, boolean isPost) {
        if (path.equals("/admin/login")) {
            return currentIsAdmin ? "REDIRECT:/admin" : "REDIRECT:/login";   // єдина форма входу - /login
        }
        if (path.equals("/admin/logout")) {
            adminToken = null;
            pendingCookie = "admin_session=; Path=/; Max-Age=0";
            return "REDIRECT:/";
        }
        if (!currentIsAdmin) {
            return "REDIRECT:/login";
        }
        switch (path) {
            case "/admin":
                return pageDashboard(data);
            case "/admin/bookings":
                return pageAdminBookings();
            case "/admin/users":
                return pageAdminUsers();
            case "/admin/rooms":
                return pageAdminRooms("", false);
            case "/admin/room":
                return pageAdminRoom(data);
            case "/admin/cancel":
                return isPost ? doAdminCancel(data) : "REDIRECT:/admin/bookings";
            case "/admin/price":
                return isPost ? doAdminPrice(data) : "REDIRECT:/admin/rooms";
            case "/admin/addroom":
                return isPost ? doAdminAddRoom(data) : "REDIRECT:/admin/rooms";
            default:
                return shell("Не знайдено", "<div class='box err'>Сторінку не знайдено.</div>", true);
        }
    }

    private String pageHome(String data) {
        int day = dateParam(data, "date", DateUtil.today());
        String type = param(data, "type");
        int maxPrice = Math.max(0, DateUtil.parseNumber(param(data, "max")));
        int minPlaces = Math.max(0, DateUtil.parseNumber(param(data, "places")));
        String sort = param(data, "sort");

        Room[] list = hotel.filterRooms(type, maxPrice, minPlaces);
        hotel.sortRooms(list, sort);

        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Ласкаво просимо до готелю &laquo;").append(esc(hotel.getName())).append("&raquo;</h1>");
        sb.append("<form method='get' action='/search' class='box'><b>Знайти вільний номер:</b> ");
        sb.append("<label>Заселення <input type='date' name='in' required value='").append(DateUtil.toIso(DateUtil.today() + 1)).append("'></label> ");
        sb.append("<label>Виселення <input type='date' name='out' required value='").append(DateUtil.toIso(DateUtil.today() + 3)).append("'></label> ");
        sb.append("<button>Шукати</button></form>");

        sb.append("<h2>Наші номери</h2>");
        sb.append("<form method='get' class='box filters'>");
        sb.append("<label>Статус на дату <input type='date' name='date' value='").append(DateUtil.toIso(day)).append("'></label>");
        sb.append("<label>Тип <select name='type'>").append(typeOptions(type)).append("</select></label>");
        sb.append("<label>Місць <select name='places'>").append(placesOptions(minPlaces)).append("</select></label>");
        sb.append("<label>Макс. ціна <input type='number' name='max' min='0' step='100' placeholder='без обмеження' style='width:130px' value='")
                .append(maxPrice > 0 ? String.valueOf(maxPrice) : "").append("'></label>");
        sb.append("<label>Сортування <select name='sort'>").append(sortOptions(sort)).append("</select></label>");
        sb.append("<button>Застосувати</button> <a class='btn light' href='/'>Скинути</a></form>");

        sb.append("<p class='muted'>Знайдено номерів: <b>").append(list.length).append("</b></p>");
        if (list.length == 0) {
            sb.append("<div class='box err'>За цими умовами номерів немає. Спробуйте змінити фільтри.</div>");
        }
        sb.append("<div class='grid'>");
        for (Room r : list) {
            Booking b = r.getBookingOn(day);
            sb.append("<div class='room-card'>");
            sb.append("<div class='thumb ").append(r.getType()).append("'>").append(r.getNumber()).append("</div>");
            sb.append("<div class='top'><b>Номер ").append(r.getNumber()).append("</b> <span class='tag ")
                    .append(r.getType()).append("'>").append(r.getType()).append("</span> <span class='stars'>")
                    .append(stars(r)).append("</span></div>");
            sb.append("<div class='price'>").append(money(r.getBasePrice())).append(" <small>/ ніч</small></div>");
            sb.append("<p>Місць: <b>").append(r.getCapacity()).append("</b> &middot; ").append(r.getFloor()).append(" поверх<br>")
                    .append(esc(r.getDetails())).append("</p>");
            if (b == null) {
                sb.append("<div class='ok'>&#10003; вільний</div>");
            } else {
                sb.append("<div class='busy'>зайнятий до ").append(DateUtil.format(b.getCheckOut())).append("</div>");
            }
            sb.append("<a class='btn' href='/room?number=").append(r.getNumber()).append("'>Детальніше</a>");
            sb.append("</div>");
        }
        sb.append("</div>");
        return shell("Номери", sb.toString(), false);
    }

    private String pageSearch(String data) {
        int in = dateParam(data, "in", -1);
        int out = dateParam(data, "out", -1);
        String type = param(data, "type");
        int maxPrice = Math.max(0, DateUtil.parseNumber(param(data, "max")));
        int minPlaces = Math.max(0, DateUtil.parseNumber(param(data, "places")));
        String sort = param(data, "sort");

        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Пошук вільних номерів</h1>");
        sb.append("<form method='get' class='box filters'>");
        sb.append("<label>Заселення <input type='date' name='in' required value='").append(in >= 0 ? DateUtil.toIso(in) : "").append("'></label>");
        sb.append("<label>Виселення <input type='date' name='out' required value='").append(out >= 0 ? DateUtil.toIso(out) : "").append("'></label>");
        sb.append("<label>Тип <select name='type'>").append(typeOptions(type)).append("</select></label>");
        sb.append("<label>Місць <select name='places'>").append(placesOptions(minPlaces)).append("</select></label>");
        sb.append("<label>Макс. ціна <input type='number' name='max' min='0' step='100' placeholder='без обмеження' style='width:130px' value='")
                .append(maxPrice > 0 ? String.valueOf(maxPrice) : "").append("'></label>");
        sb.append("<label>Сортування <select name='sort'>").append(sortOptions(sort)).append("</select></label>");
        sb.append("<button>Знайти</button></form>");

        if (in >= 0 && out >= 0) {
            if (in < DateUtil.today()) {
                sb.append("<div class='box err'>Дата заселення не може бути в минулому.</div>");
            } else if (out <= in) {
                sb.append("<div class='box err'>Дата виселення має бути пізніше дати заселення.</div>");
            } else {
                int nights = out - in;
                Room[] found = hotel.getFreeRooms(in, out, type, maxPrice, minPlaces);
                hotel.sortRooms(found, sort);
                sb.append("<h2>Вільні номери: ").append(DateUtil.format(in)).append(" - ")
                        .append(DateUtil.format(out)).append(" (").append(nights).append(" н.)</h2>");
                if (found.length == 0) {
                    sb.append("<div class='box err'>На ці дати вільних номерів за обраними умовами не знайдено.</div>");
                } else {
                    sb.append("<table><tr><th>№</th><th>Тип</th><th>Місць</th><th>Ціна / ніч</th><th>Вартість за період</th><th></th></tr>");
                    for (Room r : found) {
                        sb.append("<tr><td><b>").append(r.getNumber()).append("</b></td>");
                        sb.append("<td><span class='tag ").append(r.getType()).append("'>").append(r.getType())
                                .append("</span> <span class='stars'>").append(stars(r)).append("</span></td>");
                        sb.append("<td>").append(r.getCapacity()).append("</td>");
                        sb.append("<td>").append(money(r.getBasePrice())).append("</td>");
                        sb.append("<td>").append(money(r.calculatePrice(nights))).append("</td>");
                        sb.append("<td><a class='btn light' href='/room?number=").append(r.getNumber()).append("'>Деталі</a> ");
                        sb.append("<a class='btn' href='/book?room=").append(r.getNumber())
                                .append("&in=").append(DateUtil.toIso(in)).append("&out=").append(DateUtil.toIso(out))
                                .append("'>Забронювати</a></td></tr>");
                    }
                    sb.append("</table>");
                }
            }
        }
        return shell("Пошук", sb.toString(), false);
    }

    private String pageRoom(String data) {
        Room room = hotel.findRoom(DateUtil.parseNumber(param(data, "number")));
        if (room == null) {
            return shell("Номер", "<div class='box err'>Такого номера немає. <a href='/'>До списку номерів</a></div>", false);
        }
        int[] my = monthParams(data, room);
        int today = DateUtil.today();
        Booking now = room.getBookingOn(today);

        StringBuilder sb = new StringBuilder();
        sb.append("<p><a href='/'>&larr; Усі номери</a></p>");
        sb.append("<h1>Номер ").append(room.getNumber()).append(" <span class='tag ").append(room.getType())
                .append("'>").append(room.getType()).append("</span> <span class='stars'>").append(stars(room)).append("</span></h1>");

        sb.append("<div class='two'><div class='box'>");
        sb.append("<h2 style='margin-top:0'>Про номер</h2>");
        sb.append("<p>").append(esc(room.getDescription())).append("</p>");
        sb.append("<table class='plain'>");
        sb.append("<tr><td>Поверх</td><td><b>").append(room.getFloor()).append("</b></td></tr>");
        sb.append("<tr><td>Кількість місць</td><td><b>").append(room.getCapacity()).append("</b></td></tr>");
        sb.append("<tr><td>Ціна за ніч</td><td><b>").append(money(room.getBasePrice())).append("</b></td></tr>");
        sb.append("<tr><td>Особливості</td><td>").append(esc(room.getDetails())).append("</td></tr>");
        sb.append("<tr><td>Сьогодні</td><td>");
        sb.append(now == null ? "<span class='ok'>вільний</span>"
                : "<span class='busy'>зайнятий до " + DateUtil.format(now.getCheckOut()) + "</span>");
        sb.append("</td></tr></table></div>");

        int[] nightsList = {1, 3, 7, 10};
        boolean lux = room instanceof Lux;
        sb.append("<div class='box'><h2 style='margin-top:0'>Вартість проживання</h2><table class='plain'>");
        sb.append("<tr><th>Ночей</th><th>Вартість</th>").append(lux ? "<th>Зі сніданком</th>" : "").append("<th>У середньому за ніч</th></tr>");
        for (int n : nightsList) {
            double c = room.calculatePrice(n, false);
            sb.append("<tr><td>").append(n).append("</td><td>").append(money(c)).append("</td>");
            if (lux) {
                sb.append("<td>").append(money(room.calculatePrice(n, true))).append("</td>");
            }
            sb.append("<td>").append(money(c / n)).append("</td></tr>");
        }
        sb.append("</table></div></div>");

        sb.append("<form method='get' action='/book' class='box'><b>Забронювати цей номер:</b> ");
        sb.append("<input type='hidden' name='room' value='").append(room.getNumber()).append("'>");
        sb.append("<label>Заселення <input type='date' name='in' required value='").append(DateUtil.toIso(today + 1)).append("'></label> ");
        sb.append("<label>Виселення <input type='date' name='out' required value='").append(DateUtil.toIso(today + 3)).append("'></label> ");
        sb.append("<button>Перейти до бронювання</button>");
        if (currentUser == null) {
            sb.append(" <span class='muted'>Для бронювання потрібно увійти або зареєструватися.</span>");
        }
        sb.append("</form>");

        sb.append("<h2>Календар зайнятості</h2>");
        sb.append(calendarHtml(room, my[0], my[1], false, "/room?number=" + room.getNumber()));
        return shell("Номер " + room.getNumber(), sb.toString(), false);
    }

    private String pageBookForm(String data) {
        Room room = hotel.findRoom(DateUtil.parseNumber(param(data, "room")));
        int in = dateParam(data, "in", -1);
        int out = dateParam(data, "out", -1);
        if (room == null || in < 0 || out < 0 || out <= in) {
            return shell("Бронювання", "<div class='box err'>Некоректні дані. Спробуйте почати з "
                    + "<a href='/search'>пошуку номерів</a>.</div>", false);
        }
        if (in < DateUtil.today()) {
            return shell("Бронювання", "<div class='box err'>Дата заселення не може бути в минулому. "
                    + "<a href='/search'>Повернутися до пошуку</a></div>", false);
        }
        int nights = out - in;

        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Бронювання номера ").append(room.getNumber()).append("</h1>");
        sb.append("<div class='box'><b>").append(room.getType()).append("</b>, ").append(room.getFloor())
                .append(" поверх, місць: ").append(room.getCapacity()).append(". ").append(esc(room.getDetails())).append("<br>");
        sb.append("Період: <b>").append(DateUtil.format(in)).append(" - ").append(DateUtil.format(out))
                .append("</b> (").append(nights).append(" н.)<br>");
        if (!room.isFree(in, out)) {
            sb.append("</div><div class='box err'>Номер зайнятий на цей період. "
                    + "Перегляньте <a href='/room?number=").append(room.getNumber())
                    .append("'>календар номера</a> або <a href='/search'>знайдіть інший номер</a>.</div>");
            return shell("Бронювання", sb.toString(), false);
        }
        if (room instanceof Lux) {
            sb.append("Вартість без сніданку: <b>").append(money(room.calculatePrice(nights, false))).append("</b><br>");
            sb.append("Вартість зі сніданком: <b>").append(money(room.calculatePrice(nights, true))).append("</b>");
        } else {
            sb.append("Вартість: <b>").append(money(room.calculatePrice(nights))).append("</b>");
        }
        sb.append("</div>");

        sb.append("<form method='post' action='/book' class='box'>");
        sb.append("<input type='hidden' name='room' value='").append(room.getNumber()).append("'>");
        sb.append("<input type='hidden' name='in' value='").append(DateUtil.toIso(in)).append("'>");
        sb.append("<input type='hidden' name='out' value='").append(DateUtil.toIso(out)).append("'>");
        sb.append("<p>Гість: <b>").append(esc(currentUser.getName())).append("</b><br>");
        sb.append("Ел. пошта: <b>").append(esc(currentUser.getEmail())).append("</b></p>");
        sb.append("<label>Телефон для зв'язку <input name='phone' required value='").append(esc(currentUser.getPhone()))
                .append("' placeholder='+380501234567'></label> ");
        if (room instanceof Lux) {
            sb.append("<label><input type='checkbox' name='breakfast' value='1'> Додати сніданок</label> ");
        }
        sb.append("<p class='muted'>Адміністратор зв'яжеться з вами за телефоном або поштою, якщо виникнуть питання щодо бронювання.</p>");
        sb.append("<button>Підтвердити бронювання</button></form>");
        return shell("Бронювання", sb.toString(), false);
    }

    private String doBook(String data) {
        int number = DateUtil.parseNumber(param(data, "room"));
        int in = dateParam(data, "in", -1);
        int out = dateParam(data, "out", -1);
        String phone = Validator.cleanPhone(param(data, "phone"));
        boolean breakfast = param(data, "breakfast").equals("1");

        if (phone.equals("")) {
            return shell("Бронювання", "<div class='box err'>Некоректний телефон. Вкажіть від 10 до 13 цифр, "
                    + "наприклад +380501234567. <a href='/book?room=" + number + "&in=" + DateUtil.toIso(in)
                    + "&out=" + DateUtil.toIso(out) + "'>Повернутися до форми</a></div>", false);
        }
        if (in < DateUtil.today()) {
            return shell("Бронювання", "<div class='box err'>Дата заселення не може бути в минулому.</div>", false);
        }
        Room room = hotel.findRoom(number);
        double price = 0;
        if (room != null && out > in) {
            price = room.calculatePrice(out - in, breakfast);
        }
        int code = hotel.book(number, currentUser.getName(), phone, currentUser.getEmail(), in, out, breakfast);

        String msg;
        switch (code) {
            case 0:
                msg = "<div class='box success'><h2>Номер заброньовано!</h2>Номер <b>" + number + "</b> для <b>"
                        + esc(currentUser.getName()) + "</b><br>Період: " + DateUtil.format(in) + " - " + DateUtil.format(out)
                        + "<br>До сплати: <b>" + money(price) + "</b><br>Контакт: " + esc(phone) + ", "
                        + esc(currentUser.getEmail()) + "<br><br>"
                        + "<a class='btn' href='/my'>Мої бронювання</a> <a class='btn light' href='/'>На головну</a></div>";
                break;
            case 2:
                msg = "<div class='box err'>Номер зайнятий на цей період.</div>";
                break;
            case 3:
                msg = "<div class='box err'>Некоректні дати.</div>";
                break;
            case 4:
                msg = "<div class='box err'>Для номера вичерпано ліміт бронювань.</div>";
                break;
            default:
                msg = "<div class='box err'>Помилка бронювання.</div>";
        }
        return shell("Бронювання", msg, false);
    }

    private String pageCalc(String data) {
        int number = DateUtil.parseNumber(param(data, "number"));
        int nights = DateUtil.parseNumber(param(data, "nights"));
        boolean breakfast = param(data, "breakfast").equals("1");

        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Розрахунок вартості</h1>");
        sb.append("<form method='get' class='box'><label>Номер <select name='number'>");
        for (int i = 0; i < hotel.getRoomsCount(); i++) {
            Room r = hotel.getRoom(i);
            sb.append("<option value='").append(r.getNumber()).append("'")
                    .append(r.getNumber() == number ? " selected" : "").append(">")
                    .append(r.getNumber()).append(" - ").append(r.getType()).append("</option>");
        }
        sb.append("</select></label> <label>Ночей <input type='number' name='nights' min='1' max='365' value='")
                .append(nights > 0 ? String.valueOf(nights) : "3").append("'></label> ");
        sb.append("<label><input type='checkbox' name='breakfast' value='1'").append(breakfast ? " checked" : "")
                .append("> Сніданок (лише Lux)</label> <button>Розрахувати</button></form>");

        Room room = hotel.findRoom(number);
        if (room != null && nights >= 1 && nights <= 365) {
            double total = room.calculatePrice(nights, breakfast);
            sb.append("<div class='box success'><b>").append(room.getType()).append(" №").append(number).append("</b><br>");
            sb.append("Ціна за ніч: ").append(money(room.getBasePrice())).append("<br>");
            sb.append("Ночей: ").append(nights).append("<br>");
            sb.append("Без знижок і доплат: ").append(money(room.getBasePrice() * nights)).append("<br>");
            sb.append("<span class='big'>До сплати: ").append(money(total)).append("</span></div>");
        }
        return shell("Розрахунок", sb.toString(), false);
    }

    private String pageUserLogin(String error, String next, String email) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='login'><h1>Вхід</h1>");
        if (!error.equals("")) {
            sb.append("<div class='box err'>").append(esc(error)).append("</div>");
        }
        sb.append("<form method='post' action='/login' class='box'>");
        sb.append("<input type='hidden' name='next' value='").append(esc(next)).append("'>");
        sb.append("<label>Ел. пошта<br><input type='email' name='email' required style='width:100%;margin:6px 0 12px 0' value='")
                .append(esc(email)).append("'></label>");
        sb.append("<label>Пароль<br><input type='password' name='password' required style='width:100%;margin:6px 0 12px 0'></label>");
        sb.append("<button>Увійти</button></form>");
        sb.append("<p>Ще немає акаунта? <a href='/register?next=").append(enc(next)).append("'>Зареєструватися</a></p>");
        sb.append("<p class='muted'>Демо-гість: guest@example.com / guest123</p></div>");
        return shell("Вхід", sb.toString(), false);
    }

    private String doUserLogin(String data) {
        String email = param(data, "email").toLowerCase();
        String next = param(data, "next");

        if (email.equals(adminEmail)) {
            if (param(data, "password").equals(adminPassword)) {
                adminToken = UUID.randomUUID().toString();
                pendingCookie = "admin_session=" + adminToken + "; Path=/; HttpOnly; SameSite=Strict";
                return "REDIRECT:/admin";
            }
            return pageUserLogin("Невірна пошта або пароль.", next, email);
        }

        User u = hotel.authenticate(email, param(data, "password"));
        if (u == null) {
            return pageUserLogin("Невірна пошта або пароль.", next, email);
        }
        pendingCookie = "guest_session=" + createGuestSession(u.getEmail()) + "; Path=/; HttpOnly; SameSite=Strict";
        return "REDIRECT:" + safeNext(next, "/my");
    }

    private String pageRegister(String error, String next, String name, String email, String phone) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='login'><h1>Реєстрація гостя</h1>");
        if (!error.equals("")) {
            sb.append("<div class='box err'>").append(esc(error)).append("</div>");
        }
        sb.append("<form method='post' action='/register' class='box'>");
        sb.append("<input type='hidden' name='next' value='").append(esc(next)).append("'>");
        sb.append("<label>Ім'я та прізвище<br><input name='name' required maxlength='40' style='width:100%;margin:6px 0 10px 0' value='").append(esc(name)).append("'></label>");
        sb.append("<label>Ел. пошта<br><input type='email' name='email' required style='width:100%;margin:6px 0 10px 0' value='").append(esc(email)).append("'></label>");
        sb.append("<label>Телефон<br><input name='phone' required placeholder='+380501234567' style='width:100%;margin:6px 0 10px 0' value='").append(esc(phone)).append("'></label>");
        sb.append("<label>Пароль (мінімум 6 символів)<br><input type='password' name='password' required minlength='6' style='width:100%;margin:6px 0 10px 0'></label>");
        sb.append("<label>Повторіть пароль<br><input type='password' name='password2' required style='width:100%;margin:6px 0 10px 0'></label>");
        sb.append("<label><input type='checkbox' name='agree' value='1' required> Погоджуюсь на обробку моїх контактних даних "
                + "для зв'язку щодо бронювання</label><br>");
        sb.append("<button>Зареєструватися</button></form>");
        sb.append("<p>Уже є акаунт? <a href='/login?next=").append(enc(next)).append("'>Увійти</a></p></div>");
        return shell("Реєстрація", sb.toString(), false);
    }

    private String doRegister(String data) {
        String next = param(data, "next");
        String name = param(data, "name");
        String email = param(data, "email").toLowerCase();
        String phoneRaw = param(data, "phone");
        String phone = Validator.cleanPhone(phoneRaw);
        String pass = param(data, "password");

        String error = "";
        if (!Validator.isValidName(name)) {
            error = "Ім'я має містити від 2 до 40 символів.";
        } else if (!Validator.isValidEmail(email)) {
            error = "Некоректна адреса електронної пошти.";
        } else if (phone.equals("")) {
            error = "Некоректний телефон. Вкажіть від 10 до 13 цифр, наприклад +380501234567.";
        } else if (!Validator.isValidPassword(pass)) {
            error = "Пароль має містити щонайменше 6 символів.";
        } else if (!pass.equals(param(data, "password2"))) {
            error = "Паролі не збігаються.";
        } else if (!param(data, "agree").equals("1")) {
            error = "Потрібна згода на обробку контактних даних.";
        }
        if (error.equals("") && email.equals(adminEmail)) {
            error = "Користувач із такою поштою вже зареєстрований.";
        }
        if (error.equals("") && !hotel.registerUser(new User(name, email, phone, pass))) {
            error = "Користувач із такою поштою вже зареєстрований.";
        }
        if (!error.equals("")) {
            return pageRegister(error, next, name, email, phoneRaw);
        }
        pendingCookie = "guest_session=" + createGuestSession(email) + "; Path=/; HttpOnly; SameSite=Strict";
        return "REDIRECT:" + safeNext(next, "/my");
    }

    private String pageMy(String message, boolean isError) {
        int today = DateUtil.today();
        Booking[] all = hotel.getAllBookings();

        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Мої бронювання</h1>");
        if (!message.equals("")) {
            sb.append("<div class='box ").append(isError ? "err" : "success").append("'>").append(esc(message)).append("</div>");
        }
        sb.append("<div class='box'>Профіль: <b>").append(esc(currentUser.getName())).append("</b> &middot; ")
                .append(esc(currentUser.getEmail())).append(" &middot; ").append(esc(currentUser.getPhone())).append("</div>");

        int count = 0;
        StringBuilder rows = new StringBuilder();
        for (Booking b : all) {
            if (!b.getEmail().equalsIgnoreCase(currentUser.getEmail())) {
                continue;
            }
            count++;
            String status;
            if (b.getCheckOut() <= today) {
                status = "<span class='muted'>завершено</span>";
            } else if (b.getCheckIn() <= today) {
                status = "<span class='busy'>проживання</span>";
            } else {
                status = "<span class='ok'>очікується</span>";
            }
            rows.append("<tr><td><a href='/room?number=").append(b.getRoomNumber()).append("'><b>").append(b.getRoomNumber())
                    .append("</b></a></td><td>").append(DateUtil.format(b.getCheckIn())).append(" - ")
                    .append(DateUtil.format(b.getCheckOut())).append("</td><td>").append(b.getNights()).append("</td><td>")
                    .append(money(b.getTotalPrice())).append("</td><td>").append(status).append("</td><td>");
            if (b.getCheckIn() > today) {
                rows.append("<form method='post' action='/my/cancel' class='inline'><input type='hidden' name='room' value='")
                        .append(b.getRoomNumber()).append("'><input type='hidden' name='in' value='").append(b.getCheckIn())
                        .append("'><button class='danger' onclick=\"return confirm('Скасувати це бронювання?')\">Скасувати</button></form>");
            }
            rows.append("</td></tr>");
        }
        if (count == 0) {
            sb.append("<p>У вас ще немає бронювань. <a href='/search'>Знайти номер</a></p>");
        } else {
            sb.append("<table><tr><th>Номер</th><th>Період</th><th>Ночей</th><th>Сума</th><th>Статус</th><th></th></tr>")
                    .append(rows).append("</table>");
            sb.append("<p class='muted'>Бронювання, що вже почалося, можна змінити лише через адміністратора готелю.</p>");
        }
        return shell("Мої бронювання", sb.toString(), false);
    }

    private String doMyCancel(String data) {
        int number = DateUtil.parseNumber(param(data, "room"));
        int checkIn = DateUtil.parseNumber(param(data, "in"));
        Booking b = hotel.findBooking(number, checkIn);
        if (b == null || !b.getEmail().equalsIgnoreCase(currentUser.getEmail())) {
            return pageMy("Бронювання не знайдено.", true);
        }
        if (b.getCheckIn() <= DateUtil.today()) {
            return pageMy("Проживання вже почалося - для змін зверніться до адміністратора.", true);
        }
        hotel.cancelBookingByDate(number, checkIn);
        return pageMy("Бронювання номера " + number + " скасовано.", false);
    }

    private String pageDashboard(String data) {
        int day = dateParam(data, "date", DateUtil.today());
        int total = hotel.getRoomsCount();
        int occupied = 0;
        for (int i = 0; i < total; i++) {
            if (hotel.getRoom(i).isOccupiedOn(day)) {
                occupied++;
            }
        }
        double percent = total == 0 ? 0 : occupied * 100.0 / total;

        Booking[] all = hotel.getAllBookings();
        double revenue = 0;
        double revStandard = 0;
        double revLux = 0;
        double revSuite = 0;
        StringBuilder arrivals = new StringBuilder();
        StringBuilder departures = new StringBuilder();
        int arrCount = 0;
        int depCount = 0;
        for (Booking b : all) {
            revenue += b.getTotalPrice();
            String type = hotel.findRoom(b.getRoomNumber()).getType();
            if (type.equals("Standard")) {
                revStandard += b.getTotalPrice();
            } else if (type.equals("Lux")) {
                revLux += b.getTotalPrice();
            } else {
                revSuite += b.getTotalPrice();
            }
            if (b.getCheckIn() == day) {
                arrivals.append("<tr><td>").append(b.getRoomNumber()).append("</td><td>").append(esc(b.getGuestName()))
                        .append("</td><td>").append(contactHtml(b)).append("</td></tr>");
                arrCount++;
            }
            if (b.getCheckOut() == day) {
                departures.append("<tr><td>").append(b.getRoomNumber()).append("</td><td>").append(esc(b.getGuestName()))
                        .append("</td><td>").append(contactHtml(b)).append("</td></tr>");
                depCount++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Панель адміністратора</h1>");
        sb.append("<form method='get' class='inline'>Дата: <input type='date' name='date' value='")
                .append(DateUtil.toIso(day)).append("'> <button>Показати</button></form>");
        sb.append("<div class='cards'>");
        sb.append(card("Зайнято номерів", occupied + " з " + total + " (" + String.format("%.0f", percent) + "%)"));
        sb.append(card("Вільно", String.valueOf(total - occupied)));
        sb.append(card("Заселень цього дня", String.valueOf(arrCount)));
        sb.append(card("Виселень цього дня", String.valueOf(depCount)));
        sb.append("</div>");

        sb.append("<div class='two'><div><h2>Заселення ").append(DateUtil.format(day)).append("</h2>");
        sb.append(arrCount == 0 ? "<p>Немає.</p>" : "<table><tr><th>Номер</th><th>Гість</th><th>Контакт</th></tr>" + arrivals + "</table>");
        sb.append("</div><div><h2>Виселення ").append(DateUtil.format(day)).append("</h2>");
        sb.append(depCount == 0 ? "<p>Немає.</p>" : "<table><tr><th>Номер</th><th>Гість</th><th>Контакт</th></tr>" + departures + "</table>");
        sb.append("</div></div>");

        sb.append("<h2>Фінанси (усі бронювання): ").append(money(revenue)).append("</h2>");
        sb.append("<table><tr><th>Тип номера</th><th>Сума</th></tr>");
        sb.append("<tr><td>Standard</td><td>").append(money(revStandard)).append("</td></tr>");
        sb.append("<tr><td>Lux</td><td>").append(money(revLux)).append("</td></tr>");
        sb.append("<tr><td>Suite</td><td>").append(money(revSuite)).append("</td></tr></table>");
        sb.append("<p>Усього бронювань: <b>").append(all.length).append("</b>. Зареєстрованих гостей: <b>")
                .append(hotel.getUsersCount()).append("</b>.</p>");
        return shell("Панель", sb.toString(), true);
    }

    private String pageAdminBookings() {
        Booking[] all = hotel.getAllBookings();
        int today = DateUtil.today();
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Усі бронювання</h1>");
        if (all.length == 0) {
            sb.append("<p>Бронювань немає.</p>");
        } else {
            sb.append("<table><tr><th>Номер</th><th>Гість</th><th>Контакт</th><th>Заселення</th><th>Виселення</th><th>Сума</th><th>Статус</th><th></th></tr>");
            for (Booking b : all) {
                String status;
                if (b.getCheckOut() <= today) {
                    status = "<span class='muted'>завершено</span>";
                } else if (b.getCheckIn() <= today) {
                    status = "<span class='busy'>проживає</span>";
                } else {
                    status = "<span class='ok'>очікується</span>";
                }
                sb.append("<tr><td><b>").append(b.getRoomNumber()).append("</b></td><td>").append(esc(b.getGuestName()))
                        .append("</td><td>").append(contactHtml(b)).append("</td><td>").append(DateUtil.format(b.getCheckIn()))
                        .append("</td><td>").append(DateUtil.format(b.getCheckOut())).append("</td><td>")
                        .append(money(b.getTotalPrice())).append("</td><td>").append(status).append("</td><td>")
                        .append(cancelForm(b, "bookings")).append("</td></tr>");
            }
            sb.append("</table>");
        }
        return shell("Бронювання", sb.toString(), true);
    }

    private String pageAdminUsers() {
        Booking[] all = hotel.getAllBookings();
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Зареєстровані гості</h1>");
        if (hotel.getUsersCount() == 0) {
            sb.append("<p>Гостей поки немає.</p>");
        } else {
            sb.append("<table><tr><th>Ім'я</th><th>Ел. пошта</th><th>Телефон</th><th>Бронювань</th></tr>");
            for (int i = 0; i < hotel.getUsersCount(); i++) {
                User u = hotel.getUser(i);
                int n = 0;
                for (Booking b : all) {
                    if (b.getEmail().equalsIgnoreCase(u.getEmail())) {
                        n++;
                    }
                }
                sb.append("<tr><td>").append(esc(u.getName())).append("</td><td><a href='mailto:").append(esc(u.getEmail()))
                        .append("'>").append(esc(u.getEmail())).append("</a></td><td><a href='tel:").append(esc(u.getPhone()))
                        .append("'>").append(esc(u.getPhone())).append("</a></td><td>").append(n).append("</td></tr>");
            }
            sb.append("</table>");
        }
        return shell("Гості", sb.toString(), true);
    }

    private String pageAdminRooms(String message, boolean isError) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Керування номерами</h1>");
        if (!message.equals("")) {
            sb.append("<div class='box ").append(isError ? "err" : "success").append("'>").append(esc(message)).append("</div>");
        }
        sb.append("<table><tr><th>№</th><th>Тип</th><th>Поверх</th><th>Місць</th><th>Особливості</th><th>Ціна / ніч</th><th></th></tr>");
        for (int i = 0; i < hotel.getRoomsCount(); i++) {
            Room r = hotel.getRoom(i);
            sb.append("<tr><td><b>").append(r.getNumber()).append("</b></td><td><span class='tag ").append(r.getType())
                    .append("'>").append(r.getType()).append("</span></td><td>").append(r.getFloor()).append("</td><td>")
                    .append(r.getCapacity()).append("</td><td>").append(esc(r.getDetails())).append("</td><td>");
            sb.append("<form method='post' action='/admin/price' class='inline'><input type='hidden' name='number' value='")
                    .append(r.getNumber()).append("'><input type='number' name='price' min='100' max='1000000' step='50' value='")
                    .append((int) r.getBasePrice()).append("' style='width:100px;margin:0'> <button>Зберегти</button></form></td>");
            sb.append("<td><a class='btn light' href='/admin/room?number=").append(r.getNumber()).append("'>Календар</a></td></tr>");
        }
        sb.append("</table>");

        sb.append("<h2>Додати новий номер</h2>");
        sb.append("<form method='post' action='/admin/addroom' class='box'>");
        sb.append("<label>Номер <input type='number' name='number' min='1' max='9999' required style='width:90px'></label> ");
        sb.append("<label>Поверх <input type='number' name='floor' min='1' max='30' required style='width:70px'></label> ");
        sb.append("<label>Ціна/ніч <input type='number' name='price' min='100' step='50' required style='width:100px'></label> ");
        sb.append("<label>Тип <select name='type'><option>Standard</option><option>Lux</option><option>Suite</option></select></label><br>");
        sb.append("<label>Ліжок (Standard) / місць (Lux) / кімнат (Suite) <input type='number' name='extra' min='1' max='10' value='2' style='width:70px'></label> ");
        sb.append("<label><input type='checkbox' name='jacuzzi' value='1'> Джакузі (Lux)</label> ");
        sb.append("<label><input type='checkbox' name='breakfast' value='1'> Сніданок включено (Lux)</label> ");
        sb.append("<button>Додати номер</button></form>");
        return shell("Номери", sb.toString(), true);
    }

    private String doAdminPrice(String data) {
        Room room = hotel.findRoom(DateUtil.parseNumber(param(data, "number")));
        int price = DateUtil.parseNumber(param(data, "price"));
        if (room == null) {
            return pageAdminRooms("Такого номера немає.", true);
        }
        if (price < 100 || price > 1000000) {
            return pageAdminRooms("Ціна має бути від 100 до 1 000 000 грн.", true);
        }
        room.setBasePrice(price);
        return pageAdminRooms("Ціну номера " + room.getNumber() + " змінено на " + price + " грн.", false);
    }

    private String doAdminAddRoom(String data) {
        int number = DateUtil.parseNumber(param(data, "number"));
        int floor = DateUtil.parseNumber(param(data, "floor"));
        int price = DateUtil.parseNumber(param(data, "price"));
        int extra = DateUtil.parseNumber(param(data, "extra"));
        String type = param(data, "type");
        boolean jacuzzi = param(data, "jacuzzi").equals("1");
        boolean breakfast = param(data, "breakfast").equals("1");

        if (number < 1 || floor < 1 || price < 100) {
            return pageAdminRooms("Перевірте номер, поверх і ціну (ціна не менша за 100 грн).", true);
        }
        if (extra < 1 || extra > 10) {
            extra = 2;
        }
        Room room;
        if (type.equals("Lux")) {
            room = new Lux(number, floor, price, jacuzzi, breakfast, extra);
        } else if (type.equals("Suite")) {
            room = new Suite(number, floor, price, extra);
        } else {
            room = new Standard(number, floor, price, extra);
        }
        if (!hotel.addRoom(room)) {
            return pageAdminRooms("Не вдалося додати: номер " + number + " уже існує або немає місця.", true);
        }
        return pageAdminRooms("Номер " + number + " (" + room.getType() + ") додано.", false);
    }

    private String pageAdminRoom(String data) {
        Room room = hotel.findRoom(DateUtil.parseNumber(param(data, "number")));
        if (room == null) {
            return shell("Номер", "<div class='box err'>Такого номера немає.</div>", true);
        }
        int[] my = monthParams(data, room);

        StringBuilder sb = new StringBuilder();
        sb.append("<p><a href='/admin/rooms'>&larr; До списку номерів</a></p>");
        sb.append("<h1>Номер ").append(room.getNumber()).append(" <span class='tag ").append(room.getType())
                .append("'>").append(room.getType()).append("</span></h1>");
        sb.append("<p>").append(esc(room.getDetails())).append(". Ціна: ").append(money(room.getBasePrice())).append(" / ніч</p>");
        sb.append(calendarHtml(room, my[0], my[1], true, "/admin/room?number=" + room.getNumber()));

        sb.append("<h2>Бронювання номера</h2>");
        if (room.getBookingsCount() == 0) {
            sb.append("<p>Бронювань немає.</p>");
        } else {
            sb.append("<table><tr><th>Гість</th><th>Контакт</th><th>Заселення</th><th>Виселення</th><th>Сума</th><th></th></tr>");
            for (int i = 0; i < room.getBookingsCount(); i++) {
                Booking b = room.getBooking(i);
                sb.append("<tr><td>").append(esc(b.getGuestName())).append("</td><td>").append(contactHtml(b)).append("</td><td>")
                        .append(DateUtil.format(b.getCheckIn())).append("</td><td>").append(DateUtil.format(b.getCheckOut()))
                        .append("</td><td>").append(money(b.getTotalPrice())).append("</td><td>")
                        .append(cancelForm(b, "room")).append("</td></tr>");
            }
            sb.append("</table>");
        }
        return shell("Номер " + room.getNumber(), sb.toString(), true);
    }

    private String doAdminCancel(String data) {
        int number = DateUtil.parseNumber(param(data, "room"));
        int checkIn = DateUtil.parseNumber(param(data, "in"));
        hotel.cancelBookingByDate(number, checkIn);
        if (param(data, "back").equals("room")) {
            return "REDIRECT:/admin/room?number=" + number;
        }
        return "REDIRECT:/admin/bookings";
    }

    private String cancelForm(Booking b, String back) {
        return "<form method='post' action='/admin/cancel' class='inline'>"
                + "<input type='hidden' name='room' value='" + b.getRoomNumber() + "'>"
                + "<input type='hidden' name='in' value='" + b.getCheckIn() + "'>"
                + "<input type='hidden' name='back' value='" + back + "'>"
                + "<button class='danger' onclick=\"return confirm('Скасувати це бронювання?')\">Скасувати</button></form>";
    }

    private String contactHtml(Booking b) {
        String phone = b.getPhone();
        String email = b.getEmail();
        if (phone.equals("") && email.equals("")) {
            return "<span class='muted'>&mdash;</span>";
        }
        StringBuilder sb = new StringBuilder();
        if (!phone.equals("")) {
            sb.append("<a href='tel:").append(esc(phone)).append("'>").append(esc(phone)).append("</a>");
        }
        if (!email.equals("")) {
            if (sb.length() > 0) {
                sb.append("<br>");
            }
            sb.append("<a href='mailto:").append(esc(email)).append("'>").append(esc(email)).append("</a>");
        }
        return sb.toString();
    }

    private String createGuestSession(String email) {
        String token = UUID.randomUUID().toString();
        int index = guestNext % guestTokens.length;
        guestTokens[index] = token;
        guestEmails[index] = email;
        guestNext++;
        return token;
    }

    private User findSessionUser(String token) {
        if (token.equals("")) {
            return null;
        }
        for (int i = 0; i < guestTokens.length; i++) {
            if (token.equals(guestTokens[i])) {
                return hotel.findUser(guestEmails[i]);
            }
        }
        return null;
    }

    private void removeGuestSession(String token) {
        for (int i = 0; i < guestTokens.length; i++) {
            if (token.equals(guestTokens[i])) {
                guestTokens[i] = null;
                guestEmails[i] = null;
            }
        }
    }

    private String cookieValue(String cookieHeader, String name) {
        if (cookieHeader == null) {
            return "";
        }
        String[] parts = cookieHeader.split(";");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.startsWith(name + "=")) {
                return p.substring(name.length() + 1);
            }
        }
        return "";
    }

    private String safeNext(String next, String defaultUrl) {
        if (next.startsWith("/") && !next.startsWith("//") && next.length() < 300) {
            return next;
        }
        return defaultUrl;
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String typeOptions(String selected) {
        return option("", "будь-який", selected) + option("Standard", "Standard", selected)
                + option("Lux", "Lux", selected) + option("Suite", "Suite", selected);
    }

    private String placesOptions(int selected) {
        int[] values = {0, 1, 2, 3, 4, 6, 8};
        StringBuilder sb = new StringBuilder();
        for (int v : values) {
            sb.append("<option value='").append(v).append("'").append(v == selected ? " selected" : "").append(">")
                    .append(v == 0 ? "будь-яка кількість" : "від " + v).append("</option>");
        }
        return sb.toString();
    }

    private String sortOptions(String selected) {
        return option("", "за номером кімнати", selected)
                + option("price_asc", "ціна: від меншої до більшої", selected)
                + option("price_desc", "ціна: від більшої до меншої", selected)
                + option("prestige_desc", "престижність: від найвищої", selected)
                + option("prestige_asc", "престижність: від найнижчої", selected);
    }

    private String stars(Room r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.getPrestige() / 100; i++) {
            sb.append("&#9733;");
        }
        return sb.toString();
    }

    private int[] monthParams(String data, Room room) {
        int month = DateUtil.parseNumber(param(data, "month"));
        int year = DateUtil.parseNumber(param(data, "year"));
        if (month >= 1 && month <= 12 && year >= DateUtil.BASE_YEAR && year <= DateUtil.MAX_YEAR) {
            return new int[]{month, year};
        }
        int[] t = DateUtil.fromDayNumber(DateUtil.today());
        int monthStart = DateUtil.toDayNumber(1, t[1], t[2]);
        int monthEnd = monthStart + DateUtil.daysInMonth(t[1], t[2]);
        for (int i = 0; i < room.getBookingsCount(); i++) {
            if (room.getBooking(i).overlaps(monthStart, monthEnd)) {
                return new int[]{t[1], t[2]};
            }
        }
        int best = -1;
        for (int i = 0; i < room.getBookingsCount(); i++) {
            int in = room.getBooking(i).getCheckIn();
            if (in >= monthEnd && (best < 0 || in < best)) {
                best = in;
            }
        }
        if (best >= 0) {
            int[] d = DateUtil.fromDayNumber(best);
            return new int[]{d[1], d[2]};
        }
        return new int[]{t[1], t[2]};
    }

    private String calendarHtml(Room room, int month, int year, boolean showNames, String baseUrl) {
        int prevM = month == 1 ? 12 : month - 1;
        int prevY = month == 1 ? year - 1 : year;
        int nextM = month == 12 ? 1 : month + 1;
        int nextY = month == 12 ? year + 1 : year;
        int today = DateUtil.today();

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='nav2'><a class='btn light' href='").append(baseUrl).append("&month=").append(prevM)
                .append("&year=").append(prevY).append("'>&laquo;</a>");
        sb.append("<b>").append(DateUtil.getMonthName(month)).append(" ").append(year).append("</b>");
        sb.append("<a class='btn light' href='").append(baseUrl).append("&month=").append(nextM)
                .append("&year=").append(nextY).append("'>&raquo;</a></div>");

        int first = DateUtil.toDayNumber(1, month, year);
        int offset = DateUtil.dayOfWeek(first);
        int days = DateUtil.daysInMonth(month, year);
        sb.append("<table class='cal'><tr>");
        for (int i = 0; i < 7; i++) {
            sb.append("<th>").append(DateUtil.DAY_NAMES[i]).append("</th>");
        }
        sb.append("</tr><tr>");
        int cell = 0;
        for (int i = 0; i < offset; i++) {
            sb.append("<td class='empty'></td>");
            cell++;
        }
        for (int d = 1; d <= days; d++) {
            int dn = first + d - 1;
            Booking b = room.getBookingOn(dn);
            String todayCls = dn == today ? " today" : "";
            if (b == null) {
                sb.append("<td class='free").append(todayCls).append("'>").append(d).append("</td>");
            } else {
                sb.append("<td class='busyday").append(todayCls).append("'>").append(d);
                if (showNames) {
                    sb.append("<small>").append(esc(b.getGuestName())).append("</small>");
                } else {
                    sb.append("<small>зайнято</small>");
                }
                sb.append("</td>");
            }
            cell++;
            if (cell % 7 == 0 && d < days) {
                sb.append("</tr><tr>");
            }
        }
        while (cell % 7 != 0) {
            sb.append("<td class='empty'></td>");
            cell++;
        }
        sb.append("</tr></table>");
        sb.append("<p><span class='legend free'></span> вільно &nbsp; <span class='legend busyday'></span> ніч зайнята &nbsp; "
                + "<span class='legend free today'></span> сьогодні</p>");
        return sb.toString();
    }

    private String param(String data, String name) {
        String[] pairs = data.split("&");
        for (int i = 0; i < pairs.length; i++) {
            int eq = pairs[i].indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = URLDecoder.decode(pairs[i].substring(0, eq), StandardCharsets.UTF_8);
            if (key.equals(name)) {
                return URLDecoder.decode(pairs[i].substring(eq + 1), StandardCharsets.UTF_8).trim();
            }
        }
        return "";
    }

    private int dateParam(String data, String name, int defaultValue) {
        int d = DateUtil.parseIso(param(data, name));
        return d >= 0 ? d : defaultValue;
    }

    private String money(double value) {
        return String.format("%.0f грн", value);
    }

    private String option(String value, String label, String selected) {
        return "<option value='" + value + "'" + (value.equals(selected) ? " selected" : "") + ">" + label + "</option>";
    }

    private String card(String title, String value) {
        return "<div class='card'><small>" + title + "</small><div class='num'>" + value + "</div></div>";
    }

    private String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String shell(String title, String content, boolean admin) {
        String nav;
        if (admin) {
            if (currentIsAdmin) {
                nav = "<a href='/admin'>Панель</a><a href='/admin/bookings'>Бронювання</a><a href='/admin/users'>Гості</a>"
                        + "<a href='/admin/rooms'>Номери</a><a href='/'>Сайт гостя</a><a href='/admin/logout'>Вийти</a>";
            } else {
                nav = "<a href='/'>Сайт гостя</a>";
            }
        } else {
            nav = "<a href='/'>Номери</a><a href='/search'>Пошук</a><a href='/calc'>Розрахунок</a>";
            if (currentIsAdmin) {
                nav += "<a href='/admin'>Панель адміністратора</a><a href='/admin/logout'>Вийти</a>";
            } else if (currentUser != null) {
                nav += "<a href='/my'>Мої бронювання</a><span class='who'>&#128100; " + esc(currentUser.getName())
                        + "</span><a href='/logout'>Вийти</a>";
            } else {
                nav += "<a href='/login'>Увійти</a><a class='reg' href='/register'>Реєстрація</a>";
            }
        }
        String badge = admin ? " <span class='badge'>адмін</span>" : "";
        return "<!DOCTYPE html><html lang='uk'><head><meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>" + esc(title) + " - " + esc(hotel.getName()) + "</title><style>" + CSS + "</style></head>"
                + "<body class='" + (admin ? "admin" : "guest") + "'>"
                + (admin ? "" : "<div class='announce'>Від 7 ночей у Standard - знижка 10%, кожна 7-ма ніч у Suite безкоштовно."
                + "<a href='/search'>Знайти номер</a></div>")
                + "<header><div class='wrap'><span class='logo'>" + esc(hotel.getName()) + badge + "</span><nav>"
                + nav + "</nav></div></header>"
                + (admin ? "" : "<div class='waves'></div>")
                + "<main class='wrap'>" + content + "</main>"
                + footerHtml(admin) + "</body></html>";
    }

    private String footerHtml(boolean admin) {
        int year = DateUtil.fromDayNumber(DateUtil.today())[2];
        String name = esc(hotel.getName());
        String bottom = "<div class='fbottom'><div class='wrap'><span>&copy; " + year + " " + name
                + ". Усі права захищено.</span><span>Навчальний проєкт з дисципліни &laquo;Технологія Java&raquo;</span></div></div>";
        if (admin) {
            return "<footer class='site-footer'>" + bottom + "</footer>";
        }

        String account = currentUser != null
                ? "<a href='/my'>Мої бронювання</a>"
                : "<a href='/login'>Увійти</a><a href='/register'>Реєстрація</a>";
        return "<footer class='site-footer'><div class='wrap fcols'>"
                + "<div><div class='flogo'>" + name + "</div>"
                + "<p>Затишний готель серед гір. Комфортні номери, гостинна атмосфера та чисте повітря - "
                + "усе для вашого ідеального відпочинку.</p><p class='stars'>&#9733;&#9733;&#9733;&#9733;&#9733;</p></div>"
                + "<div class='flinks'><h4>Навігація</h4><a href='/'>Наші номери</a><a href='/search'>Пошук вільних номерів</a>"
                + "<a href='/calc'>Розрахунок вартості</a>" + account + "</div>"
                + "<div class='fcontacts'><h4>Контакти</h4>"
                + "<div>&#128205; м. Яремче, вул. Гірська, 12</div>"
                + "<div>&#128222; <a href='tel:+380343456789'>+38 (034) 345-67-89</a></div>"
                + "<div>&#9993; <a href='mailto:info@karpatska-zorya.ua'>info@karpatska-zorya.ua</a></div>"
                + "<div>&#128337; Ресепшн: цілодобово</div></div>"
                + "</div>" + bottom + "</footer>";
    }

    private static final String CSS =
            ":root{--bg:#e4e2db;--paper:#fcf9f1;--teal:#0b3a40;--teal2:#072d32;--sage:#cbd5d1;--sand:#e6dcc3;--ink:#1e2a2c;--muted:#5f6d6d;--line:#d9d6cb;--ok:#2f6b55;--bad:#b3473f;--gold:#b98a2e}"
            + "*{box-sizing:border-box}"
            + "body{margin:0;font-family:Segoe UI,Helvetica,Arial,sans-serif;background:var(--bg);color:var(--ink);display:flex;flex-direction:column;min-height:100vh}"
            + ".wrap{max-width:1000px;margin:0 auto;padding:0 16px}"
            + ".announce{background:var(--teal);color:#fff;text-align:center;font-size:.85rem;padding:7px 12px}.announce a{color:#fff;text-decoration:underline;margin-left:8px}"
            + "header{background:var(--paper);color:var(--teal)}body.admin header{background:var(--teal);color:#fff}"
            + "header .wrap{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;padding:14px 16px}"
            + ".logo{font-family:Georgia,'Times New Roman',serif;font-size:1.8rem;letter-spacing:.01em}"
            + ".badge{font-family:Segoe UI,Arial,sans-serif;font-size:.7rem;background:#ffffff2e;padding:2px 9px;border-radius:10px;vertical-align:middle}"
            + "nav a{color:inherit;text-decoration:none;margin-left:20px;font-size:.95rem;padding:4px 0;border-bottom:1px solid transparent}nav a:hover{border-bottom-color:currentColor}"
            + "nav a.reg{background:var(--teal);color:#fff;padding:7px 18px;border-radius:999px;border-bottom:0}nav .who{margin-left:20px;font-weight:600}"
            + ".waves{height:80px;background:url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0naHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmcnIHZpZXdCb3g9JzAgMCAxMDAwIDkwJyBwcmVzZXJ2ZUFzcGVjdFJhdGlvPSdub25lJz48cGF0aCBkPSdNMCw0NSBDMTUwLDAgMzAwLDkwIDQ1MCw0NSBTNzUwLDAgMTAwMCw1MCcgZmlsbD0nbm9uZScgc3Ryb2tlPScjMmU1YjdhJyBzdHJva2Utd2lkdGg9JzEuMycgb3BhY2l0eT0nLjg1Jy8+PHBhdGggZD0nTTAsNjIgQzIwMCwxMDAgMzUwLDEyIDU1MCw1NSBTODUwLDkyIDEwMDAsMzgnIGZpbGw9J25vbmUnIHN0cm9rZT0nIzJlNWI3YScgc3Ryb2tlLXdpZHRoPScxLjMnIG9wYWNpdHk9Jy44NScvPjwvc3ZnPg==) 0 0/100% 100% no-repeat;margin-bottom:-24px}"
            + "main{padding-top:20px;padding-bottom:30px;flex:1;width:100%}"
            + "h1,h2,.room-card .price,.card .num,.thumb{font-family:Georgia,'Times New Roman',serif;font-weight:400}"
            + "h1{margin:.2em 0 .6em;font-size:2.3rem;color:var(--teal)}body.guest h1{text-align:center}h2{margin:1.2em 0 .5em;font-size:1.55rem;color:var(--teal)}"
            + "a{color:var(--teal)}"
            + "table{width:100%;border-collapse:collapse;background:var(--paper);border-radius:18px;overflow:hidden;margin:10px 0}"
            + "th,td{padding:11px 14px;text-align:left;border-bottom:1px solid #ece8dc}th{background:#ece8dc;font-size:.8rem;text-transform:uppercase;letter-spacing:.05em;color:var(--muted)}"
            + "table.plain{border-radius:0;background:none}table.plain th,table.plain td{padding:7px 4px;background:none}"
            + ".box{background:var(--paper);border-radius:20px;padding:18px;margin:12px 0}"
            + ".box label{margin-right:14px;display:inline-block;margin-bottom:8px}"
            + ".filters{display:flex;flex-wrap:wrap;gap:6px 16px;align-items:flex-end;border-radius:26px}"
            + ".filters label{display:flex;flex-direction:column;font-size:.82rem;color:var(--muted);margin:0}.filters input,.filters select{margin:4px 0 0 0}"
            + ".err{border-left:5px solid var(--bad);background:#f6e3df}.success{border-left:5px solid var(--ok);background:#e3eee7}"
            + "input,select{padding:9px 12px;border:1px solid var(--line);border-radius:12px;background:#fffdf7;font-size:1rem;color:var(--ink);margin-left:6px}"
            + "input:focus,select:focus{outline:2px solid var(--teal);outline-offset:1px}"
            + "button,.btn{background:var(--teal);color:#fff;border:0;border-radius:999px;padding:10px 22px;font-size:.95rem;cursor:pointer;text-decoration:none;display:inline-block}"
            + "button:hover,.btn:hover{background:var(--teal2)}.btn.light{background:var(--sage);color:var(--teal)}.btn.light:hover{background:#b9c7c2}"
            + "button.danger{background:var(--bad)}button.danger:hover{background:#963a33}form.inline{display:inline-block;margin:6px 0}"
            + ".ok{color:var(--ok);font-weight:600}.busy{color:var(--bad);font-weight:600}.muted{color:var(--muted)}.stars{color:var(--gold);letter-spacing:1px}"
            + ".tag{padding:3px 12px;border-radius:999px;font-size:.8rem;background:var(--sage);color:var(--teal)}.tag.Lux{background:var(--sand)}.tag.Suite{background:#c9d1e0}"
            + ".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:16px;margin:14px 0}"
            + ".room-card{background:var(--paper);border-radius:22px;padding:14px 14px 18px;display:flex;flex-direction:column;gap:6px}"
            + ".thumb{height:104px;border-radius:14px;background:linear-gradient(135deg,var(--sage),#e9e4d2);display:flex;align-items:center;justify-content:center;font-size:2.6rem;color:var(--teal);margin-bottom:6px}"
            + ".thumb.Lux{background:linear-gradient(135deg,#e6dcc3,#f1ead8)}.thumb.Suite{background:linear-gradient(135deg,#c9d1e0,#e4e8f0)}"
            + ".room-card .top{margin-top:4px}.room-card .price{font-size:1.6rem;color:var(--teal)}.room-card .price small{font-family:Segoe UI,Arial,sans-serif;font-size:.8rem;color:var(--muted)}"
            + ".room-card p{margin:2px 0;color:#4a5858;flex:1}.room-card .btn{align-self:flex-start;margin-top:6px}"
            + ".two{display:grid;grid-template-columns:1fr 1fr;gap:14px}@media(max-width:760px){.two{grid-template-columns:1fr}}"
            + ".cal{table-layout:fixed;text-align:center}.cal th,.cal td{text-align:center;height:62px;vertical-align:top;border:1px solid #ece8dc}"
            + ".cal td.free{background:#e4ece6}.cal td.busyday{background:#efd5cf;color:#8a3a30;font-weight:600}.cal td.today{outline:3px solid var(--teal);outline-offset:-3px}"
            + ".cal td small{display:block;font-weight:400;font-size:.7rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}"
            + ".cal td.empty{background:var(--bg)}.nav2{display:flex;gap:16px;align-items:center;margin:10px 0}"
            + ".legend{display:inline-block;width:14px;height:14px;border:1px solid #bbb;vertical-align:middle}.legend.free{background:#e4ece6}.legend.busyday{background:#efd5cf}.legend.today{outline:2px solid var(--teal)}"
            + ".cards{display:flex;gap:12px;flex-wrap:wrap;margin:14px 0}.card{flex:1;min-width:170px;background:var(--paper);padding:16px;border-radius:20px}"
            + ".card small{color:var(--muted)}.card .num{font-size:1.7rem;color:var(--teal)}.big{font-size:1.3rem;font-weight:700}"
            + ".login{max-width:400px;margin:30px auto}"
            + "h1 .stars{font-size:1.15rem;vertical-align:middle;margin-left:6px}h1 .tag{vertical-align:middle;margin-left:6px}.cal th{height:38px}"
            + ".site-footer{background:var(--teal);color:#c5d6d4;margin-top:30px;font-size:.92rem}"
            + ".fcols{display:grid;grid-template-columns:1.4fr 1fr 1.2fr;gap:28px;padding-top:36px;padding-bottom:26px}"
            + ".site-footer h4{margin:0 0 14px;color:#fff;font-size:.85rem;letter-spacing:.08em;text-transform:uppercase}"
            + ".site-footer a{color:#c5d6d4;text-decoration:none}.site-footer a:hover{color:#fff;text-decoration:underline}"
            + ".flogo{font-family:Georgia,'Times New Roman',serif;font-size:1.6rem;color:#fff;margin-bottom:10px}.site-footer p{margin:0 0 10px;line-height:1.55}"
            + ".flinks a{display:block;margin-bottom:9px}.fcontacts div{margin-bottom:9px}"
            + ".fbottom{border-top:1px solid #ffffff26;padding:14px 0;font-size:.82rem;color:#93b0ae}.fbottom .wrap{display:flex;justify-content:space-between;flex-wrap:wrap;gap:6px}"
            + "@media(max-width:760px){.fcols{grid-template-columns:1fr;gap:20px}}";
}
