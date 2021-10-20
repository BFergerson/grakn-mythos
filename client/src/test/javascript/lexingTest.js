let assert = require('assert');
let parserFacade = require('../../main-generated/javascript/ParserFacade.js');
let TypeQLLexer = require('../../main-generated/javascript/TypeQLLexer.js').TypeQLLexer;

function checkToken(tokens, index, typeName, column, text) {
    it('should have ' + typeName + ' in position ' + index, function () {
        assert.equal(tokens[index].type, TypeQLLexer[typeName]);
        assert.equal(tokens[index].column, column);
        assert.equal(tokens[index].text, text);
    });
}

describe('Basic lexing with spaces', function () {
    let tokens = parserFacade.getTokens("define man sub entity;");
    it('should return 8 tokens', function() {
        assert.equal(tokens.length, 8);
    });
    checkToken(tokens, 0, 'DEFINE', 0, "define");
    checkToken(tokens, 1, 'WS', 6, " ");
    checkToken(tokens, 2, 'LABEL_', 7, "man");
    checkToken(tokens, 3, 'WS', 10, " ");
    checkToken(tokens, 4, 'SUB_', 11, "sub");
    checkToken(tokens, 5, 'WS', 14, " ");
    checkToken(tokens, 6, 'ENTITY', 15, "entity");
    checkToken(tokens, 7, 'T__0', 21, ";");
});
