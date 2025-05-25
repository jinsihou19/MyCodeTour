// 导入样式
import 'highlight.js/styles/github-dark.css';
import 'github-markdown-css/github-markdown-dark.css';
import './index.css';

// 导入首屏必需的依赖
import hljs from 'highlight.js/lib/core';
import java from 'highlight.js/lib/languages/java';
import javascript from 'highlight.js/lib/languages/javascript';

// 注册 highlight.js 语言
hljs.registerLanguage('java', java);
hljs.registerLanguage('javascript', javascript);

// 导出首屏必需的全局变量
window.hljs = hljs;

// 首屏渲染
document.addEventListener('DOMContentLoaded', function () {
    // 代码高亮
    hljs.configure({
        languages: ['java', 'javascript']
    });
    document.querySelectorAll('pre code').forEach((block) => {
        hljs.highlightElement(block);
    });

    // 异步加载其他功能
    loadMermaid();
    loadPlantUML();
    loadExcalidraw();
});

// 异步加载 Mermaid
async function loadMermaid() {
    const mermaidElements = document.querySelectorAll('.mermaid');
    if (mermaidElements.length > 0) {
        const mermaid = await import('mermaid');
        mermaid.default.initialize({
            startOnLoad: true,
            theme: 'dark',
            securityLevel: 'loose',
            flowchart: {useMaxWidth: true},
            sequence: {useMaxWidth: true},
            gantt: {useMaxWidth: true}
        });
        window.mermaid = mermaid.default;
    }
}

// 异步加载 PlantUML
async function loadPlantUML() {
    const plantumlElements = document.querySelectorAll('.plantuml');
    if (plantumlElements.length > 0) {
        const plantumlEncoder = await import('plantuml-encoder');
        window.plantumlEncoder = plantumlEncoder.default;

        plantumlElements.forEach(function (element) {
            const encoded = plantumlEncoder.default.encode(element.textContent);
            const img = document.createElement('img');
            img.src = 'https://www.plantuml.com/plantuml/dsvg/' + encoded;
            img.style.maxWidth = '100%';
            element.innerHTML = '';
            element.appendChild(img);
        });
    }
}

// 异步加载 Excalidraw
async function loadExcalidraw() {
    const excalidrawElements = document.querySelectorAll('.excalidraw');
    if (excalidrawElements.length > 0) {
        const {exportToSvg} = await import('@excalidraw/excalidraw');
        window.exportToSvg = exportToSvg;

        for (const element of excalidrawElements) {
            const excalidrawString = element.getAttribute('data-src');
            try {
                const excalidrawData = JSON.parse(excalidrawString);

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
        }
    }
}
