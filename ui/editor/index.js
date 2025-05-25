import 'easymde/dist/easymde.min.css';
import './index.css';
import EasyMDE from 'easymde';

document.addEventListener('DOMContentLoaded', () => {
    const easyMDE = new EasyMDE({
        element: document.getElementById('editor'),
        initialValue: window.initialMarkdown || '',
        autofocus: true,
        spellChecker: false,
        status: false,
        insertTexts: {
            link: ["[", "](navigate://)"],
        },
        toolbar: ['bold', 'heading', '|', 'quote', 'unordered-list', 'ordered-list', 'table', 'code', '|', 'link', 'image', '|', 'undo', 'redo'],
        renderingConfig: {
            codeSyntaxHighlighting: true,
        }
    });
    window.easyMDE = easyMDE;

    if (window.onEditorChange) {
        easyMDE.codemirror.on('change', () => {
            eval(window.onEditorChange);
        });
    }
}); 