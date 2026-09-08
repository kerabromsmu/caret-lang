const examples = {
  functions: {
    token: '_', title: 'Partial application',
    copy: 'Each underscore leaves one argument to supply later. Here, between receives its limits immediately and returns a one-argument function.',
    code: `<span class="cm">// partial application with a hole</span>\nbetween low value high =\n  value >= low and value <= high\n\ninside = between 0 _ 10\nprint inside 7\n<span class="op">// true</span>`
  },
  lambdas: {
    token: '->', title: 'Lambdas and higher-order calls',
    copy: 'A compact arrow expression creates a function. Passing even to filter uses the same whitespace application as every other call.',
    code: `<span class="cm">// functions are ordinary values</span>\nnumbers = [1 2 3 4]\neven = value -> value % 2 == 0\n\nprint filter numbers even\n<span class="op">// [ 2 4 ]</span>`
  },
  collections: {
    token: '[?~]', title: 'Null is not missing',
    copy: 'Export markers name the public fields of a Collection. A present null field returns ?, while optional access to an absent field returns ~.',
    code: `<span class="cm">// exported Collection fields</span>\nperson = [\n  ^name = "Ada"\n  ^phone = ?\n]\n\nprint person.phone\nprint person.email~\n<span class="op">// ?, then ~</span>`
  },
  contracts: {
    token: '()', title: 'Refinement contracts',
    copy: 'Contracts are callable predicates and can annotate bindings. This derived contract accepts numbers only when positive also succeeds.',
    code: `<span class="cm">// combine a type and a predicate</span>\npositive value = value > 0\nPositiveNumber = contract [Number positive]\n\n(PositiveNumber) count = 3\nprint PositiveNumber count\n<span class="op">// true</span>`
  },
  reflection: {
    token: '@', title: 'Language-owned metadata',
    copy: '@ inspects a value without invoking it. Callable metadata describes the function using Caret values rather than exposing Java implementation objects.',
    code: `<span class="cm">// inspect a callable</span>\nadd a b = a + b\nmetadata = @add\n\nprint metadata.kind\nprint metadata.id\nprint metadata.remaining\n<span class="op">// Function, add, 2</span>`
  }
};

const code = document.querySelector('#code-example');
const lineNumbers = document.querySelector('#line-numbers');
const title = document.querySelector('#example-title');
const copy = document.querySelector('#example-copy');
const token = document.querySelector('#example-token');
const tabs = [...document.querySelectorAll('[data-tab]')];
const codePanel = document.querySelector('.code-stage');

function setExample(name, focus = false) {
  const example = examples[name];
  code.innerHTML = example.code;
  lineNumbers.innerHTML = example.code.split('\n').map((_, index) => String(index + 1).padStart(2, '0')).join('<br>');
  title.textContent = example.title;
  copy.textContent = example.copy;
  token.textContent = example.token;
  tabs.forEach(tab => {
    const selected = tab.dataset.tab === name;
    tab.setAttribute('aria-selected', String(selected));
    tab.tabIndex = selected ? 0 : -1;
    if (selected) {
      codePanel.setAttribute('aria-labelledby', tab.id);
      if (focus) tab.focus();
    }
  });
}

tabs.forEach((tab, index) => {
  tab.id = `example-tab-${tab.dataset.tab}`;
  tab.setAttribute('aria-controls', 'code-example-panel');
  tab.addEventListener('click', () => setExample(tab.dataset.tab));
  tab.addEventListener('keydown', event => {
    let nextIndex;
    if (event.key === 'ArrowDown' || event.key === 'ArrowRight') nextIndex = (index + 1) % tabs.length;
    if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') nextIndex = (index - 1 + tabs.length) % tabs.length;
    if (event.key === 'Home') nextIndex = 0;
    if (event.key === 'End') nextIndex = tabs.length - 1;
    if (nextIndex === undefined) return;
    event.preventDefault();
    setExample(tabs[nextIndex].dataset.tab, true);
  });
});
setExample('functions');

document.querySelector('.copy-button').addEventListener('click', async event => {
  const button = event.currentTarget;
  try {
    await navigator.clipboard.writeText(code.textContent);
    button.textContent = 'Copied';
  } catch {
    button.textContent = 'Copy failed';
  }
  setTimeout(() => { button.textContent = 'Copy'; }, 1200);
});

const toggle = document.querySelector('.menu-toggle');
const nav = document.querySelector('#site-nav');
toggle.addEventListener('click', () => {
  const open = nav.classList.toggle('open');
  toggle.setAttribute('aria-expanded', String(open));
});
nav.querySelectorAll('a').forEach(link => link.addEventListener('click', () => {
  nav.classList.remove('open');
  toggle.setAttribute('aria-expanded', 'false');
}));

const revealElements = document.querySelectorAll('.reveal');
if ('IntersectionObserver' in window) {
  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: .12 });
  revealElements.forEach(element => observer.observe(element));
} else {
  revealElements.forEach(element => element.classList.add('visible'));
}
