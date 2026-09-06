mermaid.initialize({
  startOnLoad: false,
  theme: 'neutral',
  themeVariables: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif',
    primaryColor: '#f4f4f5',
    primaryTextColor: '#18181b',
    primaryBorderColor: '#71717a',
    lineColor: '#52525b',
    secondaryColor: '#e4e4e7',
    tertiaryColor: '#f4f4f5',
    clusterBkg: '#fafafa',
    clusterBorder: '#a1a1aa',
    edgeLabelBackground: '#ffffff',
    nodeTextColor: '#18181b',
    mainBkg: '#f4f4f5'
  },
  flowchart: {
    curve: 'basis',
    htmlLabels: true,
    useMaxWidth: true
  }
});

document$.subscribe(async () => {
  const diagrams = document.querySelectorAll(".mermaid");
  const targets = [];
  for (const diagram of diagrams) {
    // PyMdown emits Mermaid fences as <pre class="mermaid"><code>...</code></pre>.
    // Mermaid expects a plain container with the raw definition as its content.
    if (diagram.matches("pre") && diagram.firstElementChild?.matches("code")) {
      const target = document.createElement("div");
      target.className = diagram.className;
      target.textContent = diagram.textContent;
      diagram.replaceWith(target);
      targets.push(target);
    } else {
      targets.push(diagram);
    }
  }

  try {
    await mermaid.run({ nodes: targets });
  } catch (error) {
    console.error("Unable to render Mermaid diagrams", error);
  }
});
