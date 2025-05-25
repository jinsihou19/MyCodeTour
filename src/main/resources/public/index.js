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

    // 处理Excalidraw文件
    const excalidrawElements = document.querySelectorAll('.excalidraw');
    excalidrawElements.forEach(async function (element) {
        const excalidrawString = element.getAttribute('data-src');
        try {
            const excalidrawData = JSON.parse(excalidrawString)

            // 创建容器
            const container = document.createElement('div');
            container.style.width = '100%';
            container.style.height = '100%';
            container.style.background = 'transparent';
            container.style.display = 'flex';
            container.style.alignItems = 'center';
            container.style.justifyContent = 'center';
            element.appendChild(container);

            // 获取源文件路径
            const sourceFile = element.getAttribute('data-source-file');
            if (sourceFile) {
                container.style.cursor = 'pointer';
                container.onclick = () => {
                    window.location.href = `navigate://${sourceFile}`;
                };
            }

            // 直接显示SVG内容
            if (excalidrawData.svg) {
                container.innerHTML = excalidrawData.svg;
            } else {
                // 渲染Excalidraw
                const {exportToSvg} = window.excalidrawLib;
                const svg = await exportToSvg({
                    elements: excalidrawData.elements || [],
                    appState: {
                        ...excalidrawData.appState,
                        exportWithDarkMode: true,
                    },
                    files: excalidrawData.files || {},
                    exportPadding: 10
                });
                // 设置SVG样式
                svg.style.width = '100%';
                svg.style.height = '100%';
                container.appendChild(svg);
            }
        } catch (error) {
            console.error('Error loading Excalidraw:', error);
            element.innerHTML = `<div style="color: red; padding: 20px; text-align: center;">Error loading Excalidraw: ${error.message}</div>`;
        }
    });
});