import {CommonTokenStream, error, InputStream, Parser, Token} from 'antlr4/index'
import {DefaultErrorStrategy} from 'antlr4/error/ErrorStrategy'
import {GraqlLexer} from "../../main-generated/javascript/GraqlLexer.js"
import {GraqlParser} from "../../main-generated/javascript/GraqlParser.js"

class ConsoleErrorListener extends error.ErrorListener {
    syntaxError(recognizer, offendingSymbol, line, column, msg, e) {
        console.log("Graql parse error: " + msg);
    }
}

export class Error {
    startLine: number;
    endLine: number;
    startCol: number;
    endCol: number;
    message: string;

    constructor(startLine: number, endLine: number, startCol: number, endCol: number, message: string) {
        this.startLine = startLine;
        this.endLine = endLine;
        this.startCol = startCol;
        this.endCol = endCol;
        this.message = message;
    }
}

class CollectorErrorListener extends error.ErrorListener {
    private errors: Error[] = []

    constructor(errors: Error[]) {
        super()
        this.errors = errors
    }

    syntaxError(recognizer, offendingSymbol, line, column, msg, e) {
        let endColumn = column + 1;
        if (offendingSymbol._text !== null) {
            endColumn = column + offendingSymbol._text.length;
        }
        this.errors.push(new Error(line, line, column, endColumn, msg));
    }
}

export function createLexer(input: String) {
    const chars = new InputStream(input);
    const lexer = new GraqlLexer(chars);
    lexer.strictMode = false;
    return lexer;
}

export function getTokens(input: String): Token[] {
    return createLexer(input).getAllTokens()
}

// function createParser(input) {
//     const lexer = createLexer(input);
//
//     return createParserFromLexer(lexer);
// }

function createParserFromLexer(lexer) {
    const tokens = new CommonTokenStream(lexer);
    return new GraqlParser(tokens);
}

// function parseTree(input) {
//     const parser = createParser(input);
//     return parser.eof_query_list();
// }

export function parseTreeStr(input) {
    const lexer = createLexer(input);
    lexer.removeErrorListeners();
    lexer.addErrorListener(new ConsoleErrorListener());

    const parser = createParserFromLexer(lexer);
    parser.removeErrorListeners();
    parser.addErrorListener(new ConsoleErrorListener());

    const tree = parser.eof_query_list();
    return tree.toStringTree(parser.ruleNames);
}

class GraqlErrorStrategy extends DefaultErrorStrategy {
    reportUnwantedToken(recognizer: Parser) {
        return super.reportUnwantedToken(recognizer);
    }

    singleTokenDeletion(recognizer: Parser) {
        const nextTokenType = recognizer.getTokenStream().LA(2);
        if (recognizer.getTokenStream().LA(1) == GraqlParser.EOF) {
            return null;
        }
        const expecting = this.getExpectedTokens(recognizer);
        if (expecting.contains(nextTokenType)) {
            this.reportUnwantedToken(recognizer);
            recognizer.consume();
            const matchedSymbol = recognizer.getCurrentToken();
            this.reportMatch(recognizer);
            return matchedSymbol;
        } else {
            return null;
        }
    }

    getExpectedTokens = function (recognizer) {
        return recognizer.getExpectedTokens();
    };

    reportMatch = function (recognizer) {
        this.endErrorCondition(recognizer);
    };
}

export function validate(input): Error[] {
    let errors: Error[] = [];

    const lexer = createLexer(input);
    lexer.removeErrorListeners();
    lexer.addErrorListener(new ConsoleErrorListener());

    const parser = createParserFromLexer(lexer);
    parser.removeErrorListeners();
    parser.addErrorListener(new CollectorErrorListener(errors));
    parser._errHandler = new GraqlErrorStrategy();

    const tree = parser.eof_query_list();
    return errors;
}
