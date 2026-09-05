mermaid.initialize({ startOnLoad: false });

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
