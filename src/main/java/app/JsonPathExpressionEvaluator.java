package app;

import com.jayway.jsonpath.JsonPath;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonPathExpressionEvaluator {

    enum TokenType {
        NUMBER, STRING, BOOLEAN, NULL, JSONPATH, OPERATOR, LPAREN, RPAREN
    }

    record Token(TokenType type, String text) {}

    // Tokenizer
    public static List<Token> tokenize(String expr) {
        Pattern TOKEN_REGEX = Pattern.compile(
            "\\s*(?:(\\d+(\\.\\d+)?)|(\"[^\"]*\")|(\\$\\.[a-zA-Z0-9_\\[\\]\\.@]+)|([-!+\\*/%()])|(==|!=|<=|>=|<|>|&&|\\|\\|)|(true|false|null))\\s*"
        );
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_REGEX.matcher(expr);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                tokens.add(new Token(TokenType.NUMBER, matcher.group(1)));
            } else if (matcher.group(3) != null) {
                tokens.add(new Token(TokenType.STRING, matcher.group(3)));
            } else if (matcher.group(4) != null) {
                tokens.add(new Token(TokenType.JSONPATH, matcher.group(4)));
            } else if (matcher.group(5) != null) {
                String op = matcher.group(5);
                if (op.equals("(")) tokens.add(new Token(TokenType.LPAREN, op));
                else if (op.equals(")")) tokens.add(new Token(TokenType.RPAREN, op));
                else tokens.add(new Token(TokenType.OPERATOR, op));
            } else if (matcher.group(6) != null) {
                tokens.add(new Token(TokenType.OPERATOR, matcher.group(6)));
            } else if (matcher.group(7) != null) {
                String boolOrNull = matcher.group(7);
                if (boolOrNull.equals("true") || boolOrNull.equals("false")) {
                    tokens.add(new Token(TokenType.BOOLEAN, boolOrNull));
                } else {
                    tokens.add(new Token(TokenType.NULL, "null"));
                }
            }
        }
        return tokens;
    }

    // Precedence map
    private static final Map<String, Integer> precedence = Map.ofEntries(
        Map.entry("!", 4),
        Map.entry("*", 3), Map.entry("/", 3), Map.entry("%", 3),
        Map.entry("+", 2), Map.entry("-", 2),
        Map.entry("==", 1), Map.entry("!=", 1), Map.entry("<", 1),
        Map.entry("<=", 1), Map.entry(">", 1), Map.entry(">=", 1),
        Map.entry("&&", 0), Map.entry("||", 0)
    );

    private static final Set<String> rightAssociative = Set.of("!");

    // Shunting Yard: convert to postfix (RPN)
    public static List<Token> toPostfix(List<Token> tokens) {
        List<Token> output = new ArrayList<>();
        Deque<Token> stack = new ArrayDeque<>();

        for (Token token : tokens) {
            switch (token.type()) {
                case NUMBER, STRING, BOOLEAN, NULL, JSONPATH -> output.add(token);
                case OPERATOR -> {
                    while (!stack.isEmpty() && stack.peek().type() == TokenType.OPERATOR &&
                           ((rightAssociative.contains(token.text()) && precedence.getOrDefault(stack.peek().text(), 0) > precedence.getOrDefault(token.text(), 0)) ||
                            (!rightAssociative.contains(token.text()) && precedence.getOrDefault(stack.peek().text(), 0) >= precedence.getOrDefault(token.text(), 0)))) {
                        output.add(stack.pop());
                    }
                    stack.push(token);
                }
                case LPAREN -> stack.push(token);
                case RPAREN -> {
                    while (!stack.isEmpty() && stack.peek().type() != TokenType.LPAREN) {
                        output.add(stack.pop());
                    }
                    if (!stack.isEmpty() && stack.peek().type() == TokenType.LPAREN) stack.pop();
                }
            }
        }
        while (!stack.isEmpty()) output.add(stack.pop());
        return output;
    }

    // Evaluator
    public static Object evaluatePostfix(List<Token> postfix, Object jsonContext) {
        Deque<Object> stack = new ArrayDeque<>();

        for (Token token : postfix) {
            switch (token.type()) {
                case BOOLEAN -> stack.push(Boolean.valueOf(token.text()));
                case NULL -> stack.push(null);
                case NUMBER -> stack.push(Double.valueOf(token.text()));
                case STRING -> stack.push(token.text().substring(1, token.text().length() - 1));
                case JSONPATH -> stack.push(JsonPath.read(jsonContext, token.text()));
                case OPERATOR -> {
                    String op = token.text();
                    if (op.equals("!")) {
                        Object a = stack.pop();
                        stack.push(!toBoolean(a));
                    } else {
                        Object b = stack.pop();
                        Object a = stack.pop();
                        stack.push(applyOperator(op, a, b));
                    }
                }
            }
        }
        return stack.pop();
    }

    private static boolean toBoolean(Object obj) {
        if (obj instanceof Boolean) return (Boolean) obj;
        if (obj instanceof Number) return ((Number) obj).doubleValue() != 0;
        if (obj instanceof String) return !((String) obj).isEmpty();
        if (obj instanceof Collection) return !((Collection<?>) obj).isEmpty();
        if (obj instanceof Map) return !((Map<?, ?>) obj).isEmpty();
        return false;
    }

    private static Object applyOperator(String op, Object a, Object b) {
        if ((a instanceof Number || a instanceof Boolean) && (b instanceof Number || b instanceof Boolean)) {
            double x = a instanceof Boolean ? ((Boolean) a ? 1 : 0) : ((Number) a).doubleValue();
            double y = b instanceof Boolean ? ((Boolean) b ? 1 : 0) : ((Number) b).doubleValue();
            return switch (op) {
                case "+" -> x + y;
                case "-" -> x - y;
                case "*" -> x * y;
                case "/" -> x / y;
                case "%" -> x % y;
                case "==" -> x == y;
                case "!=" -> x != y;
                case ">" -> x > y;
                case "<" -> x < y;
                case ">=" -> x >= y;
                case "<=" -> x <= y;
                case "&&" -> toBoolean(x) && toBoolean(y);
                case "||" -> toBoolean(x) || toBoolean(y);
                default -> throw new IllegalArgumentException("Unsupported operator: " + op);
            };
        } else {
            String x = a.toString();
            String y = b.toString();
            return switch (op) {
                case "==" -> x.equals(y);
                case "!=" -> !x.equals(y);
                case ">" -> x.compareTo(y) > 0;
                case "<" -> x.compareTo(y) < 0;
                case ">=" -> x.compareTo(y) >= 0;
                case "<=" -> x.compareTo(y) <= 0;
                case "&&" -> toBoolean(x) && toBoolean(y);
                case "||" -> toBoolean(x) || toBoolean(y);
                default -> throw new IllegalArgumentException("Unsupported operator for strings: " + op);
            };
        }
    }

    public static Object evaluate(String expression, Object jsonContext) {
        List<Token> tokens = tokenize(expression);
        List<Token> postfix = toPostfix(tokens);
        return evaluatePostfix(postfix, jsonContext);
    }

    public static Object parseJson(String json) {
        // return AppSettings.getMapper().readValue(json, Object.class);
        return JsonPath.parse(json).json();
    }

    public static boolean evaluateBoolean(String expression, Object jsonContext) {
        try {
            Object result = evaluate(expression, jsonContext);
            return toBoolean(result);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return false;
        }
    }
}
