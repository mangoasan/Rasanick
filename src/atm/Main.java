package atm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Path dataFile = Paths.get("data", "atm-data.txt");
        DataStore dataStore = new DataStore(dataFile);

        Map<String, User> users;
        try {
            users = dataStore.load();
        } catch (IOException e) {
            System.out.println("Не удалось загрузить данные из файла: " + e.getMessage());
            System.out.println("Приложение будет запущено с пустой базой пользователей.");
            users = new HashMap<>();
        }

        ConsoleUI consoleUI = new ConsoleUI(users, dataStore);
        consoleUI.start();
    }
}

class ConsoleUI {
    private final Map<String, User> users;
    private final DataStore dataStore;
    private final AuthService authService;
    private final BankService bankService;
    private final Scanner scanner;

    ConsoleUI(Map<String, User> users, DataStore dataStore) {
        this.users = users;
        this.dataStore = dataStore;
        this.authService = new AuthService(users);
        this.bankService = new BankService(users);
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        boolean running = true;

        System.out.println("ATM Console Simulator");
        System.out.println("Загружено пользователей: " + users.size());

        while (running) {
            printMainMenu();
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    register();
                    break;
                case "2":
                    login();
                    break;
                case "0":
                    saveAndExit();
                    running = false;
                    break;
                default:
                    System.out.println("Неизвестная команда. Выберите пункт меню.");
            }
        }
    }

    private void printMainMenu() {
        System.out.println();
        System.out.println("1. Регистрация");
        System.out.println("2. Вход");
        System.out.println("0. Выход");
        System.out.print("Выберите действие: ");
    }

    private void register() {
        try {
            System.out.print("Введите имя: ");
            String fullName = scanner.nextLine().trim();

            System.out.print("Введите номер карты (16 цифр): ");
            String cardNumber = scanner.nextLine().trim();

            System.out.print("Введите PIN (4 цифры): ");
            String pin = scanner.nextLine().trim();

            authService.registerUser(fullName, cardNumber, pin);
            saveQuietly();
            System.out.println("Регистрация выполнена успешно.");
        } catch (AuthException e) {
            System.out.println("Ошибка регистрации: " + e.getMessage());
        }
    }

    private void login() {
        try {
            System.out.print("Введите номер карты: ");
            String cardNumber = scanner.nextLine().trim();
            AuthService.validateCardNumber(cardNumber);

            User user = authService.findUser(cardNumber);
            if (user == null) {
                System.out.println("Пользователь с таким номером карты не найден.");
                return;
            }

            if (user.isBlocked()) {
                System.out.println("Карта заблокирована. Обратитесь в банк.");
                return;
            }

            while (!user.isBlocked()) {
                System.out.print("Введите PIN: ");
                String pin = scanner.nextLine().trim();

                try {
                    AuthService.validatePin(pin);
                    User authenticatedUser = authService.authenticate(cardNumber, pin);
                    saveQuietly();
                    System.out.println("Вход выполнен успешно.");
                    userMenu(authenticatedUser);
                    return;
                } catch (AuthException e) {
                    saveQuietly();
                    System.out.println(e.getMessage());
                    if (user.isBlocked()) {
                        return;
                    }
                }
            }
        } catch (AuthException e) {
            System.out.println("Ошибка входа: " + e.getMessage());
        }
    }

    private void userMenu(User user) {
        boolean loggedIn = true;

        while (loggedIn) {
            System.out.println();
            System.out.println("1. Посмотреть баланс");
            System.out.println("2. Пополнить счет");
            System.out.println("3. Снять наличные");
            System.out.println("4. Перевести средства");
            System.out.println("5. История операций");
            System.out.println("0. Выйти из аккаунта");
            System.out.print("Выберите действие: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    showBalance(user);
                    break;
                case "2":
                    deposit(user);
                    break;
                case "3":
                    withdraw(user);
                    break;
                case "4":
                    transfer(user);
                    break;
                case "5":
                    showHistory(user);
                    break;
                case "0":
                    loggedIn = false;
                    System.out.println("Вы вышли из аккаунта.");
                    break;
                default:
                    System.out.println("Неизвестная команда. Выберите пункт меню.");
            }
        }
    }

    private void showBalance(User user) {
        System.out.printf("Текущий баланс: %.2f%n", bankService.getBalance(user));
    }

    private void deposit(User user) {
        try {
            double amount = readAmount("Введите сумму пополнения: ");
            bankService.deposit(user, amount);
            saveQuietly();
            System.out.println("Счет успешно пополнен.");
        } catch (BankException e) {
            System.out.println("Ошибка операции: " + e.getMessage());
        }
    }

    private void withdraw(User user) {
        try {
            double amount = readAmount("Введите сумму снятия: ");
            bankService.withdraw(user, amount);
            saveQuietly();
            System.out.println("Средства успешно сняты.");
        } catch (BankException e) {
            System.out.println("Ошибка операции: " + e.getMessage());
        }
    }

    private void transfer(User user) {
        try {
            System.out.print("Введите номер карты получателя: ");
            String recipientCard = scanner.nextLine().trim();
            AuthService.validateCardNumber(recipientCard);

            double amount = readAmount("Введите сумму перевода: ");
            bankService.transfer(user, recipientCard, amount);
            saveQuietly();
            System.out.println("Перевод выполнен успешно.");
        } catch (AuthException | BankException e) {
            System.out.println("Ошибка операции: " + e.getMessage());
        }
    }

    private void showHistory(User user) {
        List<Transaction> transactions = user.getRecentTransactions(10);

        if (transactions.isEmpty()) {
            System.out.println("История операций пуста.");
            return;
        }

        System.out.println("Последние операции:");
        for (int i = 0; i < transactions.size(); i++) {
            Transaction transaction = transactions.get(i);
            System.out.printf(
                    "%d. %s | %s | %.2f | %s%n",
                    i + 1,
                    transaction.getFormattedTimestamp(),
                    transaction.getType().getLabel(),
                    transaction.getAmount(),
                    transaction.getDescription()
            );
        }
    }

    private double readAmount(String prompt) throws BankException {
        System.out.print(prompt);
        String rawValue = scanner.nextLine().trim().replace(',', '.');

        try {
            return Double.parseDouble(rawValue);
        } catch (NumberFormatException e) {
            throw new BankException("Сумма должна быть числом.");
        }
    }

    private void saveQuietly() {
        try {
            dataStore.save(users);
        } catch (IOException e) {
            System.out.println("Не удалось сохранить данные: " + e.getMessage());
        }
    }

    private void saveAndExit() {
        try {
            dataStore.save(users);
            System.out.println("Данные сохранены. Работа завершена.");
        } catch (IOException e) {
            System.out.println("Ошибка сохранения данных: " + e.getMessage());
            System.out.println("Работа завершена без гарантии сохранения изменений.");
        }
    }
}

class AuthService {
    private static final int MAX_LOGIN_ATTEMPTS = 3;

    private final Map<String, User> users;

    AuthService(Map<String, User> users) {
        this.users = users;
    }

    public User registerUser(String fullName, String cardNumber, String pin) throws AuthException {
        validateFullName(fullName);
        validateCardNumber(cardNumber);
        validatePin(pin);

        if (users.containsKey(cardNumber)) {
            throw new AuthException("Пользователь с таким номером карты уже существует.");
        }

        User user = new User(fullName, cardNumber, pin, new Account(0.0));
        users.put(cardNumber, user);
        return user;
    }

    public User authenticate(String cardNumber, String pin) throws AuthException {
        User user = findUser(cardNumber);

        if (user == null) {
            throw new AuthException("Пользователь с таким номером карты не найден.");
        }

        if (user.isBlocked()) {
            throw new AuthException("Карта заблокирована. Обратитесь в банк.");
        }

        if (user.getPin().equals(pin)) {
            user.resetFailedAttempts();
            return user;
        }

        user.incrementFailedAttempts();
        int attemptsLeft = MAX_LOGIN_ATTEMPTS - user.getFailedAttempts();

        if (attemptsLeft <= 0) {
            user.block();
            throw new AuthException("Карта заблокирована после 3 неудачных попыток.");
        }

        throw new AuthException("Неверный PIN. Осталось попыток: " + attemptsLeft);
    }

    public User findUser(String cardNumber) {
        return users.get(cardNumber);
    }

    public static void validateCardNumber(String cardNumber) throws AuthException {
        if (cardNumber == null || !cardNumber.matches("\\d{16}")) {
            throw new AuthException("Номер карты должен содержать ровно 16 цифр.");
        }
    }

    public static void validatePin(String pin) throws AuthException {
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new AuthException("PIN должен содержать ровно 4 цифры.");
        }
    }

    private void validateFullName(String fullName) throws AuthException {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new AuthException("Имя пользователя не может быть пустым.");
        }
    }
}

class BankService {
    private final Map<String, User> users;

    BankService(Map<String, User> users) {
        this.users = users;
    }

    public double getBalance(User user) {
        return user.getAccount().getBalance();
    }

    public void deposit(User user, double amount) throws BankException {
        validateAmount(amount);
        user.getAccount().deposit(amount);
        user.addTransaction(new Transaction(
                LocalDateTime.now(),
                TransactionType.DEPOSIT,
                amount,
                "Пополнение счета"
        ));
    }

    public void withdraw(User user, double amount) throws BankException {
        validateAmount(amount);

        if (user.getAccount().getBalance() < amount) {
            throw new BankException("Недостаточно средств на счете.");
        }

        user.getAccount().withdraw(amount);
        user.addTransaction(new Transaction(
                LocalDateTime.now(),
                TransactionType.WITHDRAW,
                amount,
                "Снятие наличных"
        ));
    }

    public void transfer(User sender, String recipientCard, double amount) throws BankException {
        validateAmount(amount);

        User recipient = users.get(recipientCard);
        if (recipient == null) {
            throw new BankException("Получатель с таким номером карты не найден.");
        }

        if (sender.getCardNumber().equals(recipientCard)) {
            throw new BankException("Нельзя переводить средства самому себе.");
        }

        if (sender.getAccount().getBalance() < amount) {
            throw new BankException("Недостаточно средств для перевода.");
        }

        sender.getAccount().withdraw(amount);
        recipient.getAccount().deposit(amount);

        LocalDateTime now = LocalDateTime.now();
        sender.addTransaction(new Transaction(
                now,
                TransactionType.TRANSFER_OUT,
                amount,
                "Перевод на карту " + recipientCard
        ));
        recipient.addTransaction(new Transaction(
                now,
                TransactionType.TRANSFER_IN,
                amount,
                "Перевод от карты " + sender.getCardNumber()
        ));
    }

    private void validateAmount(double amount) throws BankException {
        if (amount <= 0) {
            throw new BankException("Сумма должна быть больше нуля.");
        }
    }
    
}

class DataStore {
    private final Path filePath;

    DataStore(Path filePath) {
        this.filePath = filePath;
    }

    public Map<String, User> load() throws IOException {
        Map<String, User> users = new HashMap<>();

        if (!Files.exists(filePath)) {
            return users;
        }

        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        List<String[]> transactionRows = new ArrayList<>();

        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\|", -1);
            if (parts.length == 0) {
                continue;
            }

            if ("USER".equals(parts[0])) {
                loadUser(parts, users);
            } else if ("TX".equals(parts[0])) {
                transactionRows.add(parts);
            }
        }

        for (String[] parts : transactionRows) {
            loadTransaction(parts, users);
        }

        return users;
    }

    public void save(Map<String, User> users) throws IOException {
        List<String> lines = new ArrayList<>();

        users.values().stream()
                .sorted(Comparator.comparing(User::getCardNumber))
                .forEach(user -> {
                    lines.add(String.join("|",
                            "USER",
                            user.getCardNumber(),
                            user.getPin(),
                            Boolean.toString(user.isBlocked()),
                            Integer.toString(user.getFailedAttempts()),
                            Double.toString(user.getAccount().getBalance()),
                            encode(user.getFullName())
                    ));

                    for (Transaction transaction : user.getTransactions()) {
                        lines.add(String.join("|",
                                "TX",
                                user.getCardNumber(),
                                transaction.getTimestamp().toString(),
                                transaction.getType().name(),
                                Double.toString(transaction.getAmount()),
                                encode(transaction.getDescription())
                        ));
                    }
                });

        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }

        Files.write(filePath, lines, StandardCharsets.UTF_8);
    }

    private void loadUser(String[] parts, Map<String, User> users) {
        if (parts.length < 7) {
            return;
        }

        try {
            String cardNumber = parts[1];
            String pin = parts[2];
            boolean blocked = Boolean.parseBoolean(parts[3]);
            int failedAttempts = Integer.parseInt(parts[4]);
            double balance = Double.parseDouble(parts[5]);
            String fullName = decode(parts[6]);

            User user = new User(fullName, cardNumber, pin, new Account(balance));
            user.setBlocked(blocked);
            user.setFailedAttempts(failedAttempts);
            users.put(cardNumber, user);
        } catch (RuntimeException ignored) {
        }
    }

    private void loadTransaction(String[] parts, Map<String, User> users) {
        if (parts.length < 6) {
            return;
        }

        try {
            String cardNumber = parts[1];
            User user = users.get(cardNumber);
            if (user == null) {
                return;
            }

            LocalDateTime timestamp = LocalDateTime.parse(parts[2]);
            TransactionType type = TransactionType.valueOf(parts[3]);
            double amount = Double.parseDouble(parts[4]);
            String description = decode(parts[5]);

            user.addTransaction(new Transaction(timestamp, type, amount, description));
        } catch (RuntimeException ignored) {
        }
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}

class User {
    private final String fullName;
    private final String cardNumber;
    private final String pin;
    private final Account account;
    private final List<Transaction> transactions;

    private boolean blocked;
    private int failedAttempts;

    User(String fullName, String cardNumber, String pin, Account account) {
        this.fullName = fullName;
        this.cardNumber = cardNumber;
        this.pin = pin;
        this.account = account;
        this.transactions = new ArrayList<>();
        this.blocked = false;
        this.failedAttempts = 0;
    }

    public String getFullName() {
        return fullName;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getPin() {
        return pin;
    }

    public Account getAccount() {
        return account;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public void block() {
        this.blocked = true;
    }

    public void incrementFailedAttempts() {
        this.failedAttempts++;
    }

    public void resetFailedAttempts() {
        this.failedAttempts = 0;
    }

    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
    }

    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public List<Transaction> getRecentTransactions(int limit) {
        List<Transaction> recentTransactions = new ArrayList<>();

        for (int i = transactions.size() - 1; i >= 0 && recentTransactions.size() < limit; i--) {
            recentTransactions.add(transactions.get(i));
        }

        return recentTransactions;
    }
}

class Account {
    private double balance;

    Account(double balance) {
        this.balance = balance;
    }

    public double getBalance() {
        return balance;
    }

    public void deposit(double amount) {
        balance += amount;
    }

    public void withdraw(double amount) {
        balance -= amount;
    }
}

class Transaction {
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final LocalDateTime timestamp;
    private final TransactionType type;
    private final double amount;
    private final String description;

    Transaction(LocalDateTime timestamp, TransactionType type, double amount, String description) {
        this.timestamp = timestamp;
        this.type = type;
        this.amount = amount;
        this.description = description;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public TransactionType getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getFormattedTimestamp() {
        return timestamp.format(DISPLAY_FORMAT);
    }
}

enum TransactionType {
    DEPOSIT("Пополнение"),
    WITHDRAW("Снятие"),
    TRANSFER_IN("Входящий перевод"),
    TRANSFER_OUT("Исходящий перевод");

    private final String label;

    TransactionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

class AuthException extends Exception {
    AuthException(String message) {
        super(message);
    }
}

class BankException extends Exception {
    BankException(String message) {
        super(message);
    }
}
