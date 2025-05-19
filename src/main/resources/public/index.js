document.addEventListener('DOMContentLoaded', function () {
    hljs.configure({
        languages: ['java', 'javascript', 'python', 'xml', 'html', 'css', 'json', 'bash', 'shell']
    });
    document.querySelectorAll('pre code').forEach((block) => {
        hljs.highlightElement(block);
    });
    if (typeof mermaid !== 'undefined') {
        mermaid.initialize({
            startOnLoad: true,
            theme: 'dark',
        });
    }
    const plantumlElements = document.querySelectorAll('.plantuml');
    plantumlElements.forEach(function (element) {
        const encoded = plantumlEncoder.encode(element.textContent);
        const img = document.createElement('img');
        img.src = 'https://www.plantuml.com/plantuml/dsvg/' + encoded;
        img.style.maxWidth = '100%';
        element.innerHTML = '';
        element.appendChild(img);
    });
});