import * as monaco from 'monaco-editor';

self.MonacoEnvironment = {
    getWorkerUrl: function (moduleId, label) {
        return '../js/editor.worker.bundle.js';
    }
}

monaco.languages.register({id: 'graql'});
monaco.languages.setTokensProvider('graql', new GraqlTokensProvider.GraqlTokensProvider());
monaco.languages.setLanguageConfiguration('graql', {
    comments: {
        lineComment: "#"
    }
});

let typeFg = '7a68e8'
let literalFg = '718c00';
let idFg = 'f5871f';
let symbolsFg = '000000';
let keywordFg = 'd73a49';
let errorFg = 'ff0000';
let commentFg = '636c79'
let datatypeFg = '032f62';

monaco.editor.defineTheme('graqlTheme', {
    base: 'vs',
    inherit: false,
    rules: [
        {token: 'null.gql', foreground: symbolsFg},
        {token: 'comment.gql', foreground: commentFg},
        {token: 'type_name_.gql', foreground: typeFg},

        {token: 'string.gql', foreground: datatypeFg},
        {token: 'relation.gql', foreground: datatypeFg},
        {token: 'entity.gql', foreground: datatypeFg},
        {token: 'attribute.gql', foreground: datatypeFg},
        {token: 'rule.gql', foreground: datatypeFg},

        {token: 'string_.gql', foreground: literalFg},
        {token: 'integer_.gql', foreground: literalFg},

        {token: 'define.gql', foreground: keywordFg, fontStyle: 'bold'},
        {token: 'insert.gql', foreground: keywordFg, fontStyle: 'bold'},
        {token: 'match.gql', foreground: keywordFg, fontStyle: 'bold'},
        {token: 'get.gql', foreground: keywordFg, fontStyle: 'bold'},
        {token: 'key.gql', foreground: keywordFg},
        {token: 'datatype.gql', foreground: keywordFg},
        {token: 'has.gql', foreground: keywordFg},
        {token: 'plays.gql', foreground: keywordFg},
        {token: 'relates.gql', foreground: keywordFg},
        {token: 'when.gql', foreground: keywordFg},
        {token: 'then.gql', foreground: keywordFg},

        {token: 'isa_.gql', foreground: keywordFg},
        {token: 'sub_.gql', foreground: keywordFg},
        {token: 'var_.gql', foreground: idFg, fontStyle: 'italic'},
        //
        {token: 'ErrorCharacter.gql', foreground: errorFg}
    ]
});

window.editor = monaco.editor.create(document.getElementById('container'), {
    minimap: {
        enabled: false
    },
    automaticLayout: true,
    scrollBeyondLastLine: false,
    scrollbar: {
        verticalHasArrows: true
    },
    readOnly: legendReadOnly,
    value: [
        legendQuery
    ].join('\n'),
    language: 'graql',
    theme: 'graqlTheme'
});
window.editor.onDidChangeModelContent(function (e) {
    let code = window.editor.getValue()
    let syntaxErrors = ParserFacade.validate(code);
    let monacoErrors = [];
    for (let e of syntaxErrors) {
        monacoErrors.push({
            startLineNumber: e.startLine,
            startColumn: e.startCol,
            endLineNumber: e.endLine,
            endColumn: e.endCol,
            message: e.message,
            severity: monaco.MarkerSeverity.Error
        });
    }
    window.syntaxErrors = syntaxErrors;
    let model = monaco.editor.getModels()[0];
    monaco.editor.setModelMarkers(model, "owner", monacoErrors);
});
