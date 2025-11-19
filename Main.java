import java.util.*;

public class Main {

    private static final Map<String, Double> CONSTANTS = new HashMap<>();
    private static final Set<String> FUNCTIONS = new HashSet<>();

    static {
        CONSTANTS.put("pi", Math.PI);
        CONSTANTS.put("e", Math.E);

        FUNCTIONS.addAll(Arrays.asList(
                "sin", "cos", "tan",
                "ln", "log10",
                "sqrt", "abs", "exp",
                "pow", "diff"
        ));
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Введите выражение: ");
        String text = scanner.nextLine().trim();

        if (text.isEmpty()) {
            System.out.println("Ошибка: пустая строка.");
            return;
        }

        try {
            Parser parser = new Parser(text);
            Node root = parser.parse();

            Set<String> variables = new TreeSet<>();
            root.collectVariables(variables);

            EvaluationContext context = new EvaluationContext();
            for (String var : variables) {
                double value = readDouble(scanner, "Введите значение для " + var + ": ");
                context.setVariable(var, value);
            }

            double result = root.evaluate(context);
            System.out.println("Результат: " + result);
        } catch (ParseException | EvaluationException ex) {
            System.out.println("Ошибка: " + ex.getMessage());
        }
    }

    private static double readDouble(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim().replace(',', '.');
            if (line.isEmpty()) {
                continue;
            }
            try {
                return Double.parseDouble(line);
            } catch (NumberFormatException ex) {
                System.out.println("Ожидалось число. Попробуйте ещё раз.");
            }
        }
    }

    private enum TokenType {
        NUMBER, IDENT,
        PLUS, MINUS, STAR, SLASH, CARET,
        LPAREN, RPAREN, COMMA,
        EOF
    }

    private static final class Token {
        final TokenType type;
        final String text;
        final int position;

        Token(TokenType type, String text, int position) {
            this.type = type;
            this.text = text;
            this.position = position;
        }
    }

    private static final class Tokenizer {
        private final String input;
        private final int length;
        private int pos = 0;

        Tokenizer(String input) {
            this.input = input;
            this.length = input.length();
        }

        List<Token> tokenize() throws ParseException {
            List<Token> tokens = new ArrayList<>();
            while (pos < length) {
                char c = input.charAt(pos);
                if (Character.isWhitespace(c)) {
                    pos++;
                    continue;
                }
                if (Character.isDigit(c) || (c == '.' && nextIsDigit())) {
                    tokens.add(readNumber());
                    continue;
                }
                if (Character.isLetter(c) || c == '_') {
                    tokens.add(readIdentifier());
                    continue;
                }
                switch (c) {
                    case '+': tokens.add(simple(TokenType.PLUS)); break;
                    case '-': tokens.add(simple(TokenType.MINUS)); break;
                    case '*': tokens.add(simple(TokenType.STAR)); break;
                    case '/': tokens.add(simple(TokenType.SLASH)); break;
                    case '^': tokens.add(simple(TokenType.CARET)); break;
                    case '(': tokens.add(simple(TokenType.LPAREN)); break;
                    case ')': tokens.add(simple(TokenType.RPAREN)); break;
                    case ',': tokens.add(simple(TokenType.COMMA)); break;
                    default:
                        throw new ParseException("Недопустимый символ '" + c + "' на позиции " + pos);
                }
            }
            tokens.add(new Token(TokenType.EOF, "", pos));
            return tokens;
        }

        private boolean nextIsDigit() {
            return pos + 1 < length && Character.isDigit(input.charAt(pos + 1));
        }

        private Token simple(TokenType type) {
            Token t = new Token(type, String.valueOf(input.charAt(pos)), pos);
            pos++;
            return t;
        }

        private Token readNumber() {
            int start = pos;
            boolean hasDot = false;
            boolean hasExp = false;

            while (pos < length) {
                char c = input.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' && !hasDot) {
                    hasDot = true;
                    pos++;
                } else if ((c == 'e' || c == 'E') && !hasExp) {
                    hasExp = true;
                    pos++;
                    if (pos < length && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                        pos++;
                    }
                } else {
                    break;
                }
            }
            return new Token(TokenType.NUMBER, input.substring(start, pos), start);
        }

        private Token readIdentifier() {
            int start = pos;
            pos++;
            while (pos < length) {
                char c = input.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                    pos++;
                } else {
                    break;
                }
            }
            return new Token(TokenType.IDENT, input.substring(start, pos), start);
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int current = 0;

        Parser(String text) throws ParseException {
            this.tokens = new Tokenizer(text).tokenize();
        }

        Node parse() throws ParseException {
            Node node = parseExpression();
            expect(TokenType.EOF, "Ожидался конец выражения");
            return node;
        }

        private Node parseExpression() throws ParseException {
            Node node = parseTerm();
            while (match(TokenType.PLUS) || match(TokenType.MINUS)) {
                Token op = previous();
                Node right = parseTerm();
                node = new BinaryNode(op.text, node, right);
            }
            return node;
        }

        private Node parseTerm() throws ParseException {
            Node node = parsePower();
            while (match(TokenType.STAR) || match(TokenType.SLASH)) {
                Token op = previous();
                Node right = parsePower();
                node = new BinaryNode(op.text, node, right);
            }
            return node;
        }

        private Node parsePower() throws ParseException {
            Node node = parseUnary();
            if (match(TokenType.CARET)) {
                Token op = previous();
                Node right = parsePower();
                node = new BinaryNode(op.text, node, right);
            }
            return node;
        }

        private Node parseUnary() throws ParseException {
            if (match(TokenType.PLUS)) {
                return parseUnary();
            }
            if (match(TokenType.MINUS)) {
                return new UnaryMinusNode(parseUnary());
            }
            return parsePrimary();
        }

        private Node parsePrimary() throws ParseException {
            Token token = peek();
            switch (token.type) {
                case NUMBER:
                    advance();
                    try {
                        return new NumberNode(Double.parseDouble(token.text));
                    } catch (NumberFormatException ex) {
                        throw new ParseException("Не удалось прочитать число '" + token.text + "'");
                    }
                case IDENT:
                    advance();
                    String original = token.text;
                    String canonical = original.toLowerCase(Locale.ROOT);
                    if (match(TokenType.LPAREN)) {
                        return parseFunctionCall(original, canonical);
                    }
                    if (CONSTANTS.containsKey(canonical)) {
                        return new NumberNode(CONSTANTS.get(canonical));
                    }
                    return new VariableNode(original);
                case LPAREN:
                    advance();
                    Node inside = parseExpression();
                    expect(TokenType.RPAREN, "Ожидалась ')'");
                    return inside;
                default:
                    throw new ParseException("Неожиданный токен '" + token.text + "' на позиции " + token.position);
            }
        }

        private Node parseFunctionCall(String original, String canonical) throws ParseException {
            List<Node> args = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                do {
                    args.add(parseExpression());
                } while (match(TokenType.COMMA));
            }
            expect(TokenType.RPAREN, "Ожидалась ')' после аргументов функции " + original);
            if (!FUNCTIONS.contains(canonical)) {
                throw new ParseException("Неизвестная функция '" + original + "'");
            }
            return new FunctionNode(original, canonical, args);
        }

        private boolean match(TokenType type) {
            if (check(type)) {
                advance();
                return true;
            }
            return false;
        }

        private void expect(TokenType type, String message) throws ParseException {
            if (!check(type)) {
                throw new ParseException(message + " (позиция " + peek().position + ")");
            }
            advance();
        }

        private boolean check(TokenType type) {
            if (isAtEnd()) {
                return type == TokenType.EOF;
            }
            return peek().type == type;
        }

        private Token advance() {
            if (!isAtEnd()) {
                current++;
            }
            return previous();
        }

        private boolean isAtEnd() {
            return peek().type == TokenType.EOF;
        }

        private Token peek() {
            return tokens.get(current);
        }

        private Token previous() {
            return tokens.get(current - 1);
        }
    }

    private interface Node {
        double evaluate(EvaluationContext context);
        void collectVariables(Set<String> target);
    }

    private static final class NumberNode implements Node {
        private final double value;

        NumberNode(double value) {
            this.value = value;
        }

        @Override
        public double evaluate(EvaluationContext context) {
            return value;
        }

        @Override
        public void collectVariables(Set<String> target) {
            // ничего не добавляем
        }
    }

    private static final class VariableNode implements Node {
        private final String name;

        VariableNode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public double evaluate(EvaluationContext context) {
            return context.getVariable(name);
        }

        @Override
        public void collectVariables(Set<String> target) {
            target.add(name);
        }
    }

    private static final class UnaryMinusNode implements Node {
        private final Node inner;

        UnaryMinusNode(Node inner) {
            this.inner = inner;
        }

        @Override
        public double evaluate(EvaluationContext context) {
            return -inner.evaluate(context);
        }

        @Override
        public void collectVariables(Set<String> target) {
            inner.collectVariables(target);
        }
    }

    private static final class BinaryNode implements Node {
        private final String op;
        private final Node left;
        private final Node right;

        BinaryNode(String op, Node left, Node right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }

        @Override
        public double evaluate(EvaluationContext context) {
            double a = left.evaluate(context);
            double b = right.evaluate(context);
            switch (op) {
                case "+": return a + b;
                case "-": return a - b;
                case "*": return a * b;
                case "/":
                    if (Math.abs(b) < 1e-12) {
                        throw new EvaluationException("Деление на ноль");
                    }
                    return a / b;
                case "^": return Math.pow(a, b);
                default:
                    throw new EvaluationException("Неизвестная операция '" + op + "'");
            }
        }

        @Override
        public void collectVariables(Set<String> target) {
            left.collectVariables(target);
            right.collectVariables(target);
        }
    }

    private static final class FunctionNode implements Node {
        private final String originalName;
        private final String canonicalName;
        private final List<Node> arguments;

        FunctionNode(String originalName, String canonicalName, List<Node> arguments) {
            this.originalName = originalName;
            this.canonicalName = canonicalName;
            this.arguments = arguments;
        }

        @Override
        public double evaluate(EvaluationContext context) {
            if ("diff".equals(canonicalName)) {
                return evaluateDerivative(context);
            }
            double[] values = new double[arguments.size()];
            for (int i = 0; i < arguments.size(); i++) {
                values[i] = arguments.get(i).evaluate(context);
            }
            switch (canonicalName) {
                case "sin":
                    requireArgs(1, values.length);
                    return Math.sin(values[0]);
                case "cos":
                    requireArgs(1, values.length);
                    return Math.cos(values[0]);
                case "tan":
                    requireArgs(1, values.length);
                    return Math.tan(values[0]);
                case "ln":
                    requireArgs(1, values.length);
                    return Math.log(values[0]);
                case "log10":
                    requireArgs(1, values.length);
                    return Math.log10(values[0]);
                case "sqrt":
                    requireArgs(1, values.length);
                    if (values[0] < 0) {
                        throw new EvaluationException("sqrt: отрицательный аргумент");
                    }
                    return Math.sqrt(values[0]);
                case "abs":
                    requireArgs(1, values.length);
                    return Math.abs(values[0]);
                case "exp":
                    requireArgs(1, values.length);
                    return Math.exp(values[0]);
                case "pow":
                    requireArgs(2, values.length);
                    return Math.pow(values[0], values[1]);
                default:
                    throw new EvaluationException("Функция '" + originalName + "' пока не реализована");
            }
        }

        private double evaluateDerivative(EvaluationContext context) {
            if (arguments.size() < 3 || arguments.size() > 4) {
                throw new EvaluationException("diff ожидает 3 или 4 аргумента");
            }
            Node expressionNode = arguments.get(0);
            Node variableNode = arguments.get(1);
            if (!(variableNode instanceof VariableNode)) {
                throw new EvaluationException("Во втором аргументе diff нужно указать имя переменной");
            }
            String variableName = ((VariableNode) variableNode).getName();
            double point = arguments.get(2).evaluate(context);
            double step;
            if (arguments.size() == 4) {
                step = Math.abs(arguments.get(3).evaluate(context));
                if (step == 0.0) {
                    step = 1e-5;
                }
            } else {
                step = 1e-5 * Math.max(1.0, Math.abs(point));
            }

            VariableSnapshot snapshot = new VariableSnapshot(context, variableName);
            try {
                snapshot.set(point + step);
                double fPlus = expressionNode.evaluate(context);
                snapshot.set(point - step);
                double fMinus = expressionNode.evaluate(context);
                return (fPlus - fMinus) / (2.0 * step);
            } finally {
                snapshot.restore();
            }
        }

        private void requireArgs(int expected, int actual) {
            if (expected != actual) {
                throw new EvaluationException(
                        "Функция '" + originalName + "' ожидает " + expected + " аргумент(а), а получено " + actual
                );
            }
        }

        @Override
        public void collectVariables(Set<String> target) {
            if ("diff".equals(canonicalName)) {
                if (!arguments.isEmpty()) {
                    arguments.get(0).collectVariables(target);
                }
                if (arguments.size() > 2) {
                    arguments.get(2).collectVariables(target);
                }
                if (arguments.size() > 3) {
                    arguments.get(3).collectVariables(target);
                }
                if (arguments.size() > 1 && arguments.get(1) instanceof VariableNode) {
                    target.remove(((VariableNode) arguments.get(1)).getName());
                }
                return;
            }
            for (Node arg : arguments) {
                arg.collectVariables(target);
            }
        }
    }

    private static final class EvaluationContext {
        private final Map<String, Double> vars = new HashMap<>();

        void setVariable(String name, double value) {
            vars.put(name, value);
        }

        double getVariable(String name) {
            Double value = vars.get(name);
            if (value == null) {
                throw new EvaluationException("Переменная '" + name + "' не определена");
            }
            return value;
        }

        boolean hasVariable(String name) {
            return vars.containsKey(name);
        }

        Double peekValue(String name) {
            return vars.get(name);
        }

        void removeVariable(String name) {
            vars.remove(name);
        }
    }

    private static final class VariableSnapshot {
        private final EvaluationContext context;
        private final String name;
        private final boolean existed;
        private final double previousValue;

        VariableSnapshot(EvaluationContext context, String name) {
            this.context = context;
            this.name = name;
            this.existed = context.hasVariable(name);
            this.previousValue = existed ? context.peekValue(name) : 0.0;
        }

        void set(double value) {
            context.setVariable(name, value);
        }

        void restore() {
            if (existed) {
                context.setVariable(name, previousValue);
            } else {
                context.removeVariable(name);
            }
        }
    }

    private static final class ParseException extends Exception {
        ParseException(String message) {
            super(message);
        }
    }

    private static final class EvaluationException extends RuntimeException {
        EvaluationException(String message) {
            super(message);
        }
    }
}