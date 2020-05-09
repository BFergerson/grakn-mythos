let assert = require('assert');
let parserFacade = require('../../main-generated/javascript/ParserFacade.js');

function checkError(actualError, expectedError) {
    it('should have startLine ' + expectedError.startLine, function () {
        assert.equal(actualError.startLine, expectedError.startLine);
    });
    it('should have endLine ' + expectedError.endLine, function () {
        assert.equal(actualError.endLine, expectedError.endLine);
    });
    it('should have startCol ' + expectedError.startCol, function () {
        assert.equal(actualError.startCol, expectedError.startCol);
    });
    it('should have endCol ' + expectedError.endCol, function () {
        assert.equal(actualError.endCol, expectedError.endCol);
    });
    it('should have message ' + expectedError.message, function () {
        assert.equal(actualError.message, expectedError.message);
    });
}

function checkErrors(actualErrors, expectedErrors) {
    it('should have ' + expectedErrors.length + ' error(s)', function () {
        assert.equal(actualErrors.length, expectedErrors.length);
    });
    var i;
    for (i = 0; i < expectedErrors.length; i++) {
        checkError(actualErrors[i], expectedErrors[i]);
    }
}

function parseAndCheckErrors(input, expectedErrors) {
    let errors = parserFacade.validate(input);
    checkErrors(errors, expectedErrors);
}

describe('Basic parsing of empty file', function () {
    assert.equal(parserFacade.parseTreeStr(""), "eof_query_list")
});

describe('Basic parsing of single define query', function () {
    assert.equal(parserFacade.parseTreeStr("define man sub entity;"), "(eof_query_list (query (query_define define (statement_type (type (type_label (type_name man))) (type_property sub (type (type_label (type_native entity)))) ;))) <EOF>)")
});

describe('Validation of incomplete define query', function () {
    describe('should have recognize missing operand', function () {
        parseAndCheckErrors("define man;", [
            new parserFacade.Error(1, 1, 10, 11, "mismatched input ';' expecting {'abstract', 'type', SUB_, 'key', 'has', 'plays', 'relates', 'datatype', 'regex', 'when', 'then'}")
        ]);
    });
});
