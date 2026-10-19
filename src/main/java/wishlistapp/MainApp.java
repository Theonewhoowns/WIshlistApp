package wishlistapp;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import wishlistapp.Models.*;

/** JavaFX 8. Запросы выполняются в фоновом потоке; окно остаётся отзывчивым. */
public final class MainApp extends Application {
    // Главное окно, корневой контейнер и текущий экран приложения.
    private Stage stage;
    private StackPane root;
    private Pane page;
    // Один фоновый поток для операций БД. Daemon-поток не удерживает JVM после закрытия окон.
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "wishlist-jdbc");
                        t.setDaemon(true);
                        return t;
                    });
    // Выбранное подключение, вошедший пользователь и его объект доступа к данным.
    private Database db;
    private User user;
    private Repository repo;
    // Локальные копии данных, загруженных из БД для отображения.
    private final List<Recipient> recipients = new ArrayList<Recipient>();
    private final List<Interest> interests = new ArrayList<Interest>();
    private final List<WishList> lists = new ArrayList<WishList>();
    private final List<Wish> wishes = new ArrayList<Wish>();
    // Контролы основных экранов, содержимое которых обновляется после загрузки данных.
    private ListView<WishList> listsView;
    private ListView<Recipient> recipientsView;
    private ListView<Interest> interestsView;
    private ComboBox<Recipient> recommendRecipient;
    private TableView<Idea> ideasTable;
    private FlowPane cards;
    private TextField search;
    private ComboBox<String> status;
    private Label totals, ideaCount;
    // Подавляет реакцию на выбор списка во время программного обновления интерфейса.
    private boolean loadingUI;
    // Коды поводов для БД и соответствующие подписи имеют одинаковый порядок.
    private static final String[] OCCASIONS = {
            "ANY", "BIRTHDAY", "NEW_YEAR", "MARCH_8", "FEB_23", "ANNIVERSARY", "OTHER"
    };
    private static final String[] OCCASION_NAMES = {
            "Универсальный",
            "День рождения",
            "Новый год",
            "8 Марта",
            "23 Февраля",
            "Годовщина",
            "Другой праздник"
    };

    // Создаём главное окно, подключаем CSS и показываем экран подключения к БД.
    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("WishlistApp · Списки желаний");
        stage.setMinWidth(820);
        stage.setMinHeight(580);
        root = new StackPane();
        Scene scene = new Scene(root, 1120, 760);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
        showConnection();
    }

    // При завершении приложения останавливаем исполнитель фоновых задач.
    @Override
    public void stop() {
        worker.shutdownNow();
    }

    // Заменяем содержимое окна текущим экраном.
    private void display(Pane p) {
        page = p;
        root.getChildren().setAll(p);
    }

    // Вспомогательные методы создания кнопок, вертикальных блоков, строк и заголовков.
    private Button button(String text, Runnable action) {
        Button b = new Button(text);
        b.setOnAction(e -> action.run());
        return b;
    }

    private static VBox box(Node... nodes) {
        VBox b = new VBox(12, nodes);
        b.setPadding(new Insets(20));
        return b;
    }

    private static HBox row(Node... nodes) {
        HBox b = new HBox(8, nodes);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }

    private static Label heading(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("heading");
        return l;
    }

    // Выполняем work в фоне, а обработку результата done — в потоке JavaFX.
    private <T> void run(Callable<T> work, Consumer<T> done) {
        // На время операции блокируем экран и показываем индикатор ожидания.
        page.setDisable(true);
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(46, 46);
        StackPane cover = new StackPane(indicator);
        cover.getStyleClass().add("busy");
        root.getChildren().add(cover);
        // Запрос выполняется в call(); обработчики успеха и ошибки обновляют интерфейс.
        Task<T> task =
                new Task<T>() {
                    protected T call() throws Exception {
                        return work.call();
                    }
                };
        task.setOnSucceeded(
                e -> {
                    root.getChildren().remove(cover);
                    page.setDisable(false);
                    try {
                        done.accept(task.getValue());
                    } catch (Exception x) {
                        error(x);
                    }
                });
        task.setOnFailed(
                e -> {
                    root.getChildren().remove(cover);
                    page.setDisable(false);
                    error(task.getException());
                });
        worker.submit(task);
    }

    // После успешного изменения данных заново загружаем содержимое экранов из БД.
    private void mutate(Callable<Void> work) {
        run(work, x -> refresh());
    }

    // Показываем ошибку; типовые коды обеих СУБД переводим в понятные пользователю сообщения.
    private void error(Throwable x) {
        String message = x.getMessage();
        if (x instanceof SQLException) {
            SQLException s = (SQLException) x;
            String state = s.getSQLState();
            int code = s.getErrorCode();
            if ("23505".equals(state) || code == 2601 || code == 2627)
                message =
                        "Такое значение уже существует. Проверьте уникальность логина или названия.";
            else if ("23503".equals(state) || code == 547)
                message =
                        "Операция нарушает связи или ограничения БД. Например, нельзя удалить получателя со списками или используемый интерес.";
            else if ("23514".equals(state) || code == 51011)
                message = "Данные не соответствуют ограничениям: " + message;
            message += "\nSQLState: " + state + "; код: " + code;
        }
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.initOwner(stage);
        a.setTitle("Ошибка");
        a.setHeaderText("Операция не выполнена");
        a.setContentText(message == null ? x.getClass().getSimpleName() : message);
        a.showAndWait();
    }

    // Информационное сообщение и диалог подтверждения действий пользователя.
    private void info(String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(stage);
        a.setHeaderText(null);
        a.setContentText(text);
        a.showAndWait();
    }

    private boolean confirm(String text) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.OK, ButtonType.CANCEL);
        a.initOwner(stage);
        a.setHeaderText("Подтвердите действие");
        return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    // Экран выбора файла настроек и проверки доступности базы данных.
    private void showConnection() {
        // Используем первый аргумент запуска либо файл config/db.properties по умолчанию.
        String path =
                getParameters().getRaw().isEmpty()
                        ? "config/db.properties"
                        : getParameters().getRaw().get(0);
        TextField file = new TextField(Paths.get(path).toAbsolutePath().normalize().toString());
        // Выбор файла настроек через системный диалог.
        Button choose =
                button(
                        "Выбрать…",
                        () -> {
                            FileChooser fc = new FileChooser();
                            fc.setTitle("Файл настроек подключения");
                            fc.getExtensionFilters()
                                    .add(
                                            new FileChooser.ExtensionFilter(
                                                    "Настройки", "*.properties"));
                            File f = fc.showOpenDialog(stage);
                            if (f != null) file.setText(f.getAbsolutePath());
                        });
        // В фоне читаем настройки и проверяем схему; после успеха показываем форму входа.
        Button connect =
                button(
                        "Подключиться",
                        () -> {
                            String selected = file.getText().trim();
                            run(
                                    () -> {
                                        Database d = new Database(Config.load(Paths.get(selected)));
                                        d.verify();
                                        return d;
                                    },
                                    d -> {
                                        db = d;
                                        showLogin();
                                    });
                        });
        Label help =
                new Label(
                        "Скопируйте пример из config в db.properties и укажите логин и пароль СУБД.\nВыбранная база должна содержать обновлённую схему wishlistApp.");
        help.setWrapText(true);
        VBox pane = box(heading("Подключение к базе данных"), help, row(file, choose), connect);
        pane.setMaxWidth(760);
        HBox.setHgrow(file, Priority.ALWAYS);
        display(new StackPane(pane));
    }

    // Экран входа и регистрации пользователей приложения.
    private void showLogin() {
        // Сбрасываем прежнюю пользовательскую сессию при выходе или смене подключения.
        user = null;
        repo = null;
        TextField login = new TextField();
        login.setPromptText("Логин приложения");
        PasswordField password = new PasswordField();
        password.setPromptText("Пароль приложения");
        // Проверяем пароль через Repository; после проверки очищаем массив символов пароля.
        Button enter =
                button(
                        "Войти",
                        () -> {
                            String name = login.getText();
                            char[] secret = password.getText().toCharArray();
                            password.clear();
                            run(
                                    () -> {
                                        try {
                                            return Repository.login(db, name, secret);
                                        } finally {
                                            Arrays.fill(secret, '\0');
                                        }
                                    },
                                    u -> {
                                        if (u == null) info("Неверный логин или пароль");
                                        else {
                                            user = u;
                                            repo = new Repository(db, u);
                                            showMain();
                                        }
                                    });
                        });
        enter.setDefaultButton(true);
        // Создаём обычного пользователя. После регистрации требуется отдельный вход.
        Button register =
                button(
                        "Регистрация",
                        () -> {
                            String name = login.getText();
                            char[] secret = password.getText().toCharArray();
                            password.clear();
                            run(
                                    () -> {
                                        try {
                                            Repository.register(db, name, secret);
                                            return true;
                                        } finally {
                                            Arrays.fill(secret, '\0');
                                        }
                                    },
                                    ok ->
                                            info(
                                                    "Учётная запись создана. Введите пароль и нажмите «Войти»."));
                        });
        VBox pane =
                box(
                        heading("Мой вишлист"),
                        new Label("Вход · " + db.config.type + " / " + db.config.schema),
                        login,
                        password,
                        row(enter, register),
                        button("Сменить подключение", this::showConnection));
        pane.setMaxWidth(450);
        display(new StackPane(pane));
    }

    // Собираем основной интерфейс и запускаем первоначальную загрузку данных.
    private void showMain() {
        listsView = new ListView<WishList>();
        recipientsView = new ListView<Recipient>();
        interestsView = new ListView<Interest>();
        recommendRecipient = new ComboBox<Recipient>();
        // Выбор списка загружает его желания; поиск и статус фильтруют уже загруженные данные.
        listsView.setPrefWidth(260);
        listsView
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!loadingUI) loadWishes();
                        });
        cards = new FlowPane(16, 16);
        cards.setPadding(new Insets(16));
        search = new TextField();
        search.setPromptText("Поиск по названию");
        search.textProperty().addListener((o, a, b) -> renderWishes());
        status =
                new ComboBox<String>(
                        FXCollections.observableArrayList("Все", "Не приобретено", "Приобретено"));
        status.getSelectionModel().selectFirst();
        status.valueProperty().addListener((o, a, b) -> renderWishes());
        totals = new Label();
        ScrollPane scroll = new ScrollPane(cards);
        scroll.setFitToWidth(true);
        // Левая панель: создание, изменение и удаление собственных списков.
        VBox listSide =
                box(
                        heading("Списки"),
                        listsView,
                        row(
                                button("Создать", () -> listDialog(null)),
                                button(
                                        "Изменить",
                                        () -> {
                                            WishList w = selectedList();
                                            if (w != null) listDialog(w);
                                        })),
                        button(
                                "Удалить список",
                                () -> {
                                    WishList w = selectedList();
                                    if (w != null
                                            && confirm(
                                            "Удалить список «"
                                                    + w.name
                                                    + "» вместе с желаниями?"))
                                        mutate(
                                                () -> {
                                                    repo.deleteList(w.id);
                                                    return null;
                                                });
                                }));
        VBox.setVgrow(listsView, Priority.ALWAYS);
        listSide.setPrefWidth(300);
        // Правая панель: фильтры, итоговая стоимость и карточки желаний.
        VBox wishPane =
                box(
                        row(
                                search,
                                status,
                                button(
                                        "Добавить желание",
                                        () -> {
                                            if (selectedList() != null) wishDialog(null);
                                        })),
                        totals,
                        scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        HBox.setHgrow(search, Priority.ALWAYS);
        BorderPane listsPane = new BorderPane(wishPane);
        listsPane.setLeft(listSide);
        // Управление получателями и их интересами; связанные данные дополнительно защищены
        // ограничениями БД.
        VBox recPane =
                box(
                        heading("Получатели подарков"),
                        new Label("Выберите получателя для изменения имени, возраста и интересов."),
                        recipientsView,
                        row(
                                button(
                                        "Добавить",
                                        () ->
                                                recipientDialog(
                                                        null, Collections.<Integer>emptySet())),
                                button(
                                        "Изменить",
                                        () -> {
                                            Recipient r =
                                                    recipientsView
                                                            .getSelectionModel()
                                                            .getSelectedItem();
                                            if (r != null)
                                                run(
                                                        () -> repo.recipientInterests(r.id),
                                                        ids -> recipientDialog(r, ids));
                                        }),
                                button(
                                        "Удалить",
                                        () -> {
                                            Recipient r =
                                                    recipientsView
                                                            .getSelectionModel()
                                                            .getSelectedItem();
                                            if (r != null
                                                    && confirm(
                                                    "Удалить получателя «"
                                                            + r.name
                                                            + "»? Если есть списки, удаление будет запрещено."))
                                                mutate(
                                                        () -> {
                                                            repo.deleteRecipient(r.id);
                                                            return null;
                                                        });
                                        })));
        VBox.setVgrow(recipientsView, Priority.ALWAYS);
        // Общие вкладки доступны всем; вкладка управления интересами добавляется только
        // администратору.
        TabPane tabs =
                new TabPane(
                        tab("Мои списки", listsPane),
                        tab("Получатели", recPane),
                        tab("Каталог и подбор", catalogPane()));
        if (user.admin()) tabs.getTabs().add(tab("Интересы", interestPane()));
        // Верхняя панель: текущий пользователь, выбранная СУБД, обновление данных и выход.
        BorderPane body = new BorderPane(tabs);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header =
                row(
                        heading("WishlistApp"),
                        new Label(user.username + (user.admin() ? " · администратор" : "")),
                        spacer,
                        new Label(db.config.type),
                        button("Обновить", this::refresh),
                        button("Выйти", this::showLogin));
        header.setPadding(new Insets(16));
        body.setTop(header);
        display(body);
        refresh();
    }

    // Вспомогательные методы для создания вкладок и получения выбранного списка.
    private static Tab tab(String name, Node node) {
        Tab t = new Tab(name, node);
        t.setClosable(false);
        return t;
    }

    private WishList selectedList() {
        return listsView.getSelectionModel().getSelectedItem();
    }

    // Общий результат фоновой загрузки для последующего обновления интерфейса.
    private static final class Bundle {
        List<Recipient> recipients;
        List<Interest> interests;
        List<WishList> lists;
        List<Idea> ideas;
    }

    // Перечитываем получателей, интересы, списки и каталог, по возможности сохраняя выбор списка.
    private void refresh() {
        Integer selected = selectedList() == null ? null : selectedList().id;
        run(
                () -> {
                    Bundle b = new Bundle();
                    b.recipients = repo.recipients();
                    b.interests = repo.interests();
                    b.lists = repo.lists();
                    b.ideas = repo.catalog();
                    return b;
                },
                b -> {
                    // Обновляем коллекции и контролы без промежуточных загрузок желаний.
                    loadingUI = true;
                    recipients.clear();
                    recipients.addAll(b.recipients);
                    interests.clear();
                    interests.addAll(b.interests);
                    lists.clear();
                    lists.addAll(b.lists);
                    listsView.getItems().setAll(lists);
                    recipientsView.getItems().setAll(recipients);
                    interestsView.getItems().setAll(interests);
                    recommendRecipient.getItems().setAll(recipients);
                    if (!recipients.isEmpty()) recommendRecipient.getSelectionModel().selectFirst();
                    ideasTable.getItems().setAll(b.ideas);
                    ideaCount.setText("Каталог: " + b.ideas.size() + " идей");
                    for (WishList w : lists)
                        if (selected != null && w.id == selected) {
                            listsView.getSelectionModel().select(w);
                            break;
                        }
                    if (selectedList() == null && !lists.isEmpty())
                        listsView.getSelectionModel().selectFirst();
                    // После восстановления выбора загружаем желания текущего списка.
                    loadingUI = false;
                    loadWishes();
                });
    }

    // Получаем желания выбранного списка; при отсутствии выбора очищаем карточки.
    private void loadWishes() {
        WishList w = selectedList();
        if (w == null) {
            wishes.clear();
            renderWishes();
            return;
        }
        run(
                () -> repo.wishes(w.id),
                items -> {
                    wishes.clear();
                    wishes.addAll(items);
                    renderWishes();
                });
    }

    // Перерисовываем карточки с учётом поиска и статуса, рассчитываем общие итоги списка.
    private void renderWishes() {
        if (cards == null) return;
        cards.getChildren().clear();
        BigDecimal remaining = BigDecimal.ZERO;
        int bought = 0;
        String term = search.getText().trim().toLowerCase(Locale.ROOT);
        int state = status.getSelectionModel().getSelectedIndex();
        for (Wish w : wishes) {
            // Итоги считаем до фильтрации: они относятся ко всем желаниям выбранного списка.
            if (w.purchased) bought++;
            else remaining = remaining.add(w.price);
            if (!w.title.toLowerCase(Locale.ROOT).contains(term)
                    || (state == 1 && w.purchased)
                    || (state == 2 && !w.purchased)) continue;
            // Карточка содержит изображение, описание, цену и кнопки изменения данных.
            ImageView picture = new ImageView(Images.load(w.image));
            picture.setFitWidth(150);
            picture.setFitHeight(150);
            picture.setPreserveRatio(true);
            Label title = new Label(w.title);
            title.getStyleClass().add("card-title");
            title.setWrapText(true);
            Label desc = new Label(w.description == null ? "Без описания" : w.description);
            desc.setWrapText(true);
            desc.setMaxHeight(72);
            Label price =
                    new Label(w.price.toPlainString() + " ₽" + (w.purchased ? " · Куплено" : ""));
            price.getStyleClass().add("price");
            VBox card =
                    box(
                            picture,
                            title,
                            desc,
                            price,
                            button(
                                    w.purchased ? "Отменить покупку" : "Отметить покупку",
                                    () ->
                                            mutate(
                                                    () -> {
                                                        repo.setPurchased(w.id, !w.purchased);
                                                        return null;
                                                    })),
                            row(
                                    button("Изменить", () -> wishDialog(w)),
                                    button(
                                            "Удалить",
                                            () -> {
                                                if (confirm("Удалить «" + w.title + "»?"))
                                                    mutate(
                                                            () -> {
                                                                repo.deleteWish(w.id);
                                                                return null;
                                                            });
                                            })));
            card.setPrefWidth(235);
            card.setMaxWidth(235);
            card.getStyleClass().add("card");
            if (w.purchased) card.getStyleClass().add("purchased");
            cards.getChildren().add(card);
        }
        totals.setText(
                "Позиций: "
                        + wishes.size()
                        + " · приобретено: "
                        + bought
                        + " · осталось купить: "
                        + remaining.setScale(2).toPlainString()
                        + " ₽");
        // Пояснение для пустого списка или отсутствия совпадений с фильтрами.
        if (cards.getChildren().isEmpty())
            cards.getChildren()
                    .add(
                            new Label(
                                    selectedList() == null
                                            ? "Создайте список, чтобы добавить желания"
                                            : "Нет желаний по выбранным условиям"));
    }

    // Вкладка каталога: просмотр идей, подбор подарков и административные операции.
    private Pane catalogPane() {
        ideasTable = new TableView<Idea>();
        addColumn(ideasTable, "Название", i -> i.title, 230);
        addColumn(ideasTable, "Цена, ₽", i -> i.minPrice + " — " + i.maxPrice, 150);
        addColumn(ideasTable, "Возраст", i -> i.minAge + "–" + i.maxAge, 85);
        addColumn(ideasTable, "Повод", i -> occasionName(i.occasion), 150);
        addColumn(ideasTable, "Совпадений", i -> String.valueOf(i.matches), 95);
        if (user.admin()) addColumn(ideasTable, "Активна", i -> i.active ? "Да" : "Нет", 85);
        // При выборе идеи показываем её описание и изображение.
        TextArea detail = new TextArea();
        detail.setEditable(false);
        detail.setWrapText(true);
        detail.setPrefRowCount(3);
        ImageView image = new ImageView();
        image.setFitWidth(110);
        image.setFitHeight(110);
        image.setPreserveRatio(true);
        ideasTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, a, b) -> {
                            detail.setText(b == null ? "" : b.description);
                            image.setImage(Images.load(b == null ? null : b.image));
                        });
        // Условия подбора: сохранённый получатель, бюджет и повод.
        TextField budget = new TextField("5000");
        budget.setPrefWidth(100);
        ComboBox<String> occasion = occasionBox("ANY");
        ideaCount = new Label();
        // Проверяем бюджет и передаём условия подбора в Repository.
        Button find =
                button(
                        "Подобрать",
                        () -> {
                            Recipient r = recommendRecipient.getValue();
                            if (r == null) {
                                info("Сначала добавьте и выберите получателя");
                                return;
                            }
                            try {
                                BigDecimal amount = Validation.money(budget.getText());
                                String code = occasionCode(occasion);
                                run(
                                        () -> repo.recommend(r.id, amount, code),
                                        items -> {
                                            ideasTable.getItems().setAll(items);
                                            ideaCount.setText(
                                                    "Подбор для «"
                                                            + r.name
                                                            + "»: "
                                                            + items.size()
                                                            + " идей");
                                        });
                            } catch (Exception e) {
                                error(e);
                            }
                        });
        // Возвращаем весь доступный пользователю каталог вместо результатов подбора.
        Button all =
                button(
                        "Весь каталог",
                        () ->
                                run(
                                        repo::catalog,
                                        items -> {
                                            ideasTable.getItems().setAll(items);
                                            ideaCount.setText("Каталог: " + items.size() + " идей");
                                        }));
        // Добавление идеи в собственный список доступно всем пользователям.
        HBox actions = row(button("Добавить в список", this::addIdeaToList));
        // Администратор получает кнопки управления каталогом; Repository также проверяет роль.
        if (user.admin())
            actions.getChildren()
                    .addAll(
                            button(
                                    "Новая идея",
                                    () -> ideaDialog(null, Collections.<Integer>emptySet())),
                            button(
                                    "Изменить идею",
                                    () -> {
                                        Idea i = ideasTable.getSelectionModel().getSelectedItem();
                                        if (i != null)
                                            run(
                                                    () -> repo.ideaInterests(i.id),
                                                    ids -> ideaDialog(i, ids));
                                    }),
                            button(
                                    "Включить / отключить",
                                    () -> {
                                        Idea i = ideasTable.getSelectionModel().getSelectedItem();
                                        if (i != null)
                                            mutate(
                                                    () -> {
                                                        repo.setIdeaActive(i.id, !i.active);
                                                        return null;
                                                    });
                                    }));
        HBox detailRow = row(image, detail);
        HBox.setHgrow(detail, Priority.ALWAYS);
        VBox p =
                box(
                        heading("Каталог и подбор подарков"),
                        row(
                                new Label("Получатель:"),
                                recommendRecipient,
                                new Label("Бюджет:"),
                                budget,
                                occasion),
                        row(find, all),
                        ideaCount,
                        ideasTable,
                        detailRow,
                        actions);
        VBox.setVgrow(ideasTable, Priority.ALWAYS);
        return p;
    }

    // Создаём текстовую колонку таблицы и задаём способ получения значения из объекта.
    private static <T> void addColumn(
            TableView<T> table, String name, Function<T, String> value, int width) {
        TableColumn<T, String> c = new TableColumn<T, String>(name);
        c.setCellValueFactory(x -> new ReadOnlyStringWrapper(value.apply(x.getValue())));
        c.setPrefWidth(width);
        table.getColumns().add(c);
    }

    // Административная вкладка справочника интересов. Используемые интересы удалить нельзя.
    private Pane interestPane() {
        VBox p =
                box(
                        heading("Справочник интересов"),
                        interestsView,
                        row(
                                button("Добавить", () -> interestDialog(null)),
                                button(
                                        "Изменить",
                                        () -> {
                                            Interest i =
                                                    interestsView
                                                            .getSelectionModel()
                                                            .getSelectedItem();
                                            if (i != null) interestDialog(i);
                                        }),
                                button(
                                        "Удалить",
                                        () -> {
                                            Interest i =
                                                    interestsView
                                                            .getSelectionModel()
                                                            .getSelectedItem();
                                            if (i != null
                                                    && confirm(
                                                    "Удалить интерес «"
                                                            + i.name
                                                            + "»? Используемый интерес удалить нельзя."))
                                                mutate(
                                                        () -> {
                                                            repo.deleteInterest(i.id);
                                                            return null;
                                                        });
                                        })));
        VBox.setVgrow(interestsView, Priority.ALWAYS);
        return p;
    }

    // Преобразуем коды поводов в подписи и обратно, создаём список выбора повода.
    private static String occasionName(String code) {
        for (int i = 0; i < OCCASIONS.length; i++)
            if (OCCASIONS[i].equals(code)) return OCCASION_NAMES[i];
        return code;
    }

    private static ComboBox<String> occasionBox(String code) {
        ComboBox<String> b =
                new ComboBox<String>(FXCollections.observableArrayList(OCCASION_NAMES));
        for (int i = 0; i < OCCASIONS.length; i++)
            if (OCCASIONS[i].equals(code)) b.getSelectionModel().select(i);
        if (b.getValue() == null) b.getSelectionModel().selectFirst();
        return b;
    }

    private static String occasionCode(ComboBox<String> box) {
        return OCCASIONS[box.getSelectionModel().getSelectedIndex()];
    }

    // Общий диалог редактирования: поля в сетке, прокрутка и кнопки OK/Отмена.
    private final class Form {
        final Dialog<Boolean> dialog = new Dialog<Boolean>();
        final GridPane grid = new GridPane();
        int row;

        Form(String title) {
            dialog.initOwner(stage);
            dialog.setTitle(title);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            grid.setHgap(12);
            grid.setVgap(10);
            grid.setPadding(new Insets(20));
            ScrollPane scroll = new ScrollPane(grid);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportWidth(520);
            scroll.setMaxHeight(600);
            dialog.getDialogPane().setContent(scroll);
            dialog.setResultConverter(b -> b == ButtonType.OK);
        }

        // Добавляем строку формы: подпись слева, элемент ввода справа.
        void add(String label, Node node) {
            grid.add(new Label(label), 0, row);
            grid.add(node, 1, row++);
            GridPane.setHgrow(node, Priority.ALWAYS);
        }

        // При ошибке проверки отменяем обработку OK, чтобы диалог оставался открытым.
        boolean show(Runnable validate) {
            dialog.getDialogPane()
                    .lookupButton(ButtonType.OK)
                    .addEventFilter(
                            ActionEvent.ACTION,
                            e -> {
                                try {
                                    validate.run();
                                } catch (Exception x) {
                                    e.consume();
                                    error(x);
                                }
                            });
            return dialog.showAndWait().orElse(false);
        }
    }

    // Создаём поля ввода, заменяя отсутствующие значения пустой строкой.
    private static TextField field(String s) {
        return new TextField(s == null ? "" : s);
    }

    private static TextArea area(String s) {
        TextArea a = new TextArea(s == null ? "" : s);
        a.setPrefRowCount(3);
        a.setWrapText(true);
        return a;
    }

    // Список интересов с множественным выбором и восстановлением текущих назначений.
    private ListView<Interest> interestSelector(Set<Integer> selected) {
        ListView<Interest> v = new ListView<Interest>(FXCollections.observableArrayList(interests));
        v.setPrefHeight(150);
        v.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        for (int i = 0; i < interests.size(); i++)
            if (selected.contains(interests.get(i).id)) v.getSelectionModel().select(i);
        return v;
    }

    // Получаем идентификаторы выбранных интересов для сохранения связей в БД.
    private static Set<Integer> selectedInterests(ListView<Interest> v) {
        Set<Integer> out = new HashSet<Integer>();
        for (Interest i : v.getSelectionModel().getSelectedItems()) out.add(i.id);
        return out;
    }

    // Форма получателя: при r == null создаём нового, иначе редактируем существующего.
    private void recipientDialog(Recipient r, Set<Integer> ids) {
        Form f = new Form(r == null ? "Новый получатель" : "Изменить получателя");
        TextField name = field(r == null ? "" : r.name),
                age = field(r == null ? "25" : String.valueOf(r.age));
        ListView<Interest> selected = interestSelector(ids);
        f.add("Имя", name);
        f.add("Возраст", age);
        f.add("Интересы (Ctrl + щелчок)", selected);
        if (f.show(
                () -> {
                    Validation.text(name.getText(), 100, "Имя");
                    Validation.age(age.getText());
                })) {
            String n = name.getText();
            int a = Validation.age(age.getText());
            Set<Integer> set = selectedInterests(selected);
            mutate(
                    () -> {
                        repo.saveRecipient(r == null ? null : r.id, n, a, set);
                        return null;
                    });
        }
    }

    // Форма создания или изменения названия и назначения списка желаний.
    private void listDialog(WishList w) {
        Form f = new Form(w == null ? "Новый список" : "Изменить список");
        TextField name = field(w == null ? "" : w.name);
        // Вариант «Для себя» существует только в интерфейсе: в БД ему соответствует recipient_id =
        // NULL.
        Recipient self = new Recipient(0, "Для себя", 0);
        ComboBox<Recipient> recipient = new ComboBox<Recipient>();
        recipient.getItems().add(self);
        recipient.getItems().addAll(recipients);
        recipient.setValue(self);
        recipient.setConverter(
                new javafx.util.StringConverter<Recipient>() {
                    public String toString(Recipient r) {
                        return r == null ? "" : r.id == 0 ? "Для себя" : r.toString();
                    }

                    public Recipient fromString(String s) {
                        return null;
                    }
                });
        if (w != null && w.recipientId != null)
            for (Recipient r : recipients) if (r.id == w.recipientId) recipient.setValue(r);
        f.add("Название", name);
        f.add("Назначение", recipient);
        if (f.show(() -> Validation.text(name.getText(), 100, "Название"))) {
            String n = name.getText();
            Integer id = recipient.getValue().id == 0 ? null : recipient.getValue().id;
            mutate(
                    () -> {
                        repo.saveList(w == null ? null : w.id, n, id);
                        return null;
                    });
        }
    }

    // Выбор или удаление изображения в форме; копирование файла выполняется при сохранении.
    private HBox imagePicker(TextField path, File[] selected) {
        path.setEditable(false);
        return row(
                path,
                button(
                        "Фото…",
                        () -> {
                            FileChooser fc = new FileChooser();
                            fc.setTitle("Изображение");
                            fc.getExtensionFilters()
                                    .add(
                                            new FileChooser.ExtensionFilter(
                                                    "Изображения", "*.png", "*.jpg", "*.jpeg"));
                            File file = fc.showOpenDialog(stage);
                            if (file != null) {
                                selected[0] = file;
                                path.setText(file.getAbsolutePath());
                            }
                        }),
                button(
                        "Убрать",
                        () -> {
                            selected[0] = null;
                            path.clear();
                        }));
    }

    // Форма желания: после проверки полей импортируем новое изображение и сохраняем данные через
    // Repository.
    private void wishDialog(Wish w) {
        WishList list = selectedList();
        if (list == null) return;
        Form f = new Form(w == null ? "Добавить желание" : "Изменить желание");
        TextField title = field(w == null ? "" : w.title),
                price = field(w == null ? "0" : w.price.toPlainString()),
                image = field(w == null ? "" : w.image);
        TextArea desc = area(w == null ? "" : w.description);
        File[] picked = {null};
        f.add("Название", title);
        f.add("Описание", desc);
        f.add("Планируемая цена, ₽", price);
        f.add("Изображение", imagePicker(image, picked));
        if (f.show(
                () -> {
                    Validation.text(title.getText(), 150, "Название");
                    Validation.optional(desc.getText(), 2000, "Описание");
                    Validation.money(price.getText());
                })) {
            String t = title.getText(), d = desc.getText(), path = image.getText();
            BigDecimal amount = Validation.money(price.getText());
            File photo = picked[0];
            mutate(
                    () -> {
                        String saved = photo == null ? path : Images.importFile(photo);
                        repo.saveWish(
                                w == null ? null : w.id,
                                list.id,
                                w == null ? null : w.ideaId,
                                t,
                                d,
                                amount,
                                saved);
                        return null;
                    });
        }
    }

    // Копируем выбранную активную идею в собственный список с указанной планируемой ценой.
    private void addIdeaToList() {
        Idea i = ideasTable.getSelectionModel().getSelectedItem();
        if (i == null) {
            info("Выберите идею в каталоге");
            return;
        }
        if (lists.isEmpty()) {
            info("Сначала создайте список желаний");
            return;
        }
        if (!i.active) {
            info("Эта идея отключена. Сначала включите её в каталоге.");
            return;
        }
        Form f = new Form("Добавить идею в список");
        ComboBox<WishList> target =
                new ComboBox<WishList>(FXCollections.observableArrayList(lists));
        target.setValue(selectedList() == null ? lists.get(0) : selectedList());
        TextField price = field(i.maxPrice.toPlainString());
        f.add("Список", target);
        f.add("Планируемая цена, ₽", price);
        if (f.show(() -> Validation.money(price.getText()))) {
            int listId = target.getValue().id;
            BigDecimal amount = Validation.money(price.getText());
            mutate(
                    () -> {
                        repo.saveWish(null, listId, i.id, i.title, i.description, amount, i.image);
                        return null;
                    });
        }
    }

    // Форма добавления или переименования интереса.
    private void interestDialog(Interest i) {
        Form f = new Form(i == null ? "Новый интерес" : "Изменить интерес");
        TextField name = field(i == null ? "" : i.name);
        f.add("Название", name);
        if (f.show(() -> Validation.text(name.getText(), 100, "Название"))) {
            String n = name.getText();
            mutate(
                    () -> {
                        repo.saveInterest(i == null ? null : i.id, n);
                        return null;
                    });
        }
    }

    // Административная форма идеи: цены, возраст, повод, интересы, изображение и активность.
    private void ideaDialog(Idea i, Set<Integer> ids) {
        Form f = new Form(i == null ? "Новая идея" : "Изменить идею");
        TextField title = field(i == null ? "" : i.title),
                min = field(i == null ? "0" : i.minPrice.toPlainString()),
                max = field(i == null ? "0" : i.maxPrice.toPlainString()),
                minAge = field(i == null ? "0" : String.valueOf(i.minAge)),
                maxAge = field(i == null ? "120" : String.valueOf(i.maxAge)),
                image = field(i == null ? "" : i.image);
        TextArea desc = area(i == null ? "" : i.description);
        CheckBox active = new CheckBox("Участвует в подборе");
        active.setSelected(i == null || i.active);
        ComboBox<String> occasion = occasionBox(i == null ? "ANY" : i.occasion);
        ListView<Interest> selected = interestSelector(ids);
        File[] picked = {null};
        f.add("Название", title);
        f.add("Описание", desc);
        f.add("Цена от, ₽", min);
        f.add("Цена до, ₽", max);
        f.add("Возраст от", minAge);
        f.add("Возраст до", maxAge);
        f.add("Повод", occasion);
        f.add("Интересы (Ctrl + щелчок)", selected);
        f.add("Изображение", imagePicker(image, picked));
        f.add("Активность", active);
        if (f.show(
                () -> {
                    Validation.text(title.getText(), 150, "Название");
                    Validation.optional(desc.getText(), 2000, "Описание");
                    // Проверяем, что нижние границы цены и возраста не превышают верхние.
                    if (Validation.money(min.getText()).compareTo(Validation.money(max.getText()))
                            > 0) throw new IllegalArgumentException("Цена от больше цены до");
                    if (Validation.age(minAge.getText()) > Validation.age(maxAge.getText()))
                        throw new IllegalArgumentException("Возраст от больше возраста до");
                })) {
            String t = title.getText(),
                    d = desc.getText(),
                    code = occasionCode(occasion),
                    path = image.getText();
            BigDecimal lo = Validation.money(min.getText()), hi = Validation.money(max.getText());
            int a = Validation.age(minAge.getText()), b = Validation.age(maxAge.getText());
            boolean enabled = active.isSelected();
            Set<Integer> set = selectedInterests(selected);
            File photo = picked[0];
            // Сохраняем идею и её интересы; после успеха обновляем интерфейс.
            mutate(
                    () -> {
                        String saved = photo == null ? path : Images.importFile(photo);
                        repo.saveIdea(
                                i == null ? null : i.id,
                                t,
                                d,
                                lo,
                                hi,
                                a,
                                b,
                                code,
                                saved,
                                enabled,
                                set);
                        return null;
                    });
        }
    }
}
