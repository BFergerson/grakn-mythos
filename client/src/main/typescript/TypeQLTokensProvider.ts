import * as monaco from 'monaco-editor'
import {createLexer} from './ParserFacade'
import {error} from 'antlr4/index'
import ILineTokens = monaco.languages.ILineTokens;
import IToken = monaco.languages.IToken;

export class TypeQLState implements monaco.languages.IState {
    clone(): monaco.languages.IState {
        return new TypeQLState();
    }

    equals(other: monaco.languages.IState): boolean {
        return true;
    }
}

export class TypeQLTokensProvider implements monaco.languages.TokensProvider {
    getInitialState(): monaco.languages.IState {
        return new TypeQLState();
    }

    tokenize(line: string, state: monaco.languages.IState): monaco.languages.ILineTokens {
        // So far we ignore the state, which is not great for performance reasons
        return tokensForLine(line);
    }
}

const EOF = -1;

class TypeQLToken implements IToken {
    scopes: string;
    startIndex: number;

    constructor(ruleName: String, startIndex: number) {
        if (ruleName == null) {
            this.scopes = "null.gql";
        } else {
            this.scopes = ruleName.toLowerCase() + ".gql";
        }
        this.startIndex = startIndex;
    }
}

class TypeQLLineTokens implements ILineTokens {
    endState: monaco.languages.IState;
    tokens: monaco.languages.IToken[];

    constructor(tokens: monaco.languages.IToken[]) {
        this.endState = new TypeQLState();
        this.tokens = tokens;
    }
}

export function tokensForLine(input: string): monaco.languages.ILineTokens {
    let errorStartingPoints: number[] = [];

    class ErrorCollectorListener extends error.ErrorListener {
        syntaxError(recognizer, offendingSymbol, line, column, msg, e) {
            errorStartingPoints.push(column)
        }
    }

    const lexer = createLexer(input);
    lexer.removeErrorListeners();
    let errorListener = new ErrorCollectorListener();
    lexer.addErrorListener(errorListener);
    let done = false;
    let myTokens: monaco.languages.IToken[] = [];
    do {
        let token = lexer.nextToken();
        if (token == null) {
            done = true
        } else {
            if (token.type == EOF) {
                done = true;
            } else {
                let tokenTypeName = lexer.symbolicNames[token.type];
                let myToken = new TypeQLToken(tokenTypeName, token.column);
                myTokens.push(myToken);
            }
        }
    } while (!done);

    for (let e of errorStartingPoints) {
        myTokens.push(new TypeQLToken("error.tql", e));
    }
    myTokens.sort((a, b) => (a.startIndex > b.startIndex) ? 1 : -1)

    return new TypeQLLineTokens(myTokens);
}
