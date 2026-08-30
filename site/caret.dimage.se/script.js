const examples = {
  functions: {
    token: '_', title: 'Currying & holes',
    copy: 'Each underscore introduces an argument to supply later. Here, between receives 0 and 10 immediately and leaves its middle argument open.',
    code: `<span class="cm">// partial application with a hole</span>\nbetween low value high =\n  value >= low and value <= high\n\ninside = between 0 _ 10\n\nprint inside 7  <span class="op">// true</span>`
  },
  collections: {
    token: '[]', title: 'Persistent collections',
    copy: 'The prototype provides immutable sequences and dictionaries. Updates return new values, while safe lookup returns missing when no value exists.',
    code: `<span class="cm">// immutable sequence operations</span>\nfirst = seqAdd seqEmpty "first"\nitems = seqAdd first "second"\n\nprint seqGet items 0\nprint seqGet items 99\n<span class="op">// first, then ~</span>`
  },
  contracts: {
    token: '()', title: 'Contracts',
    copy: 'Contract clauses sit directly before bindings, parameters, and results. Derived contracts are ordinary values constructed with contract.',
    code: `<span class="cm">// parameter and result contracts</span>\nNumeric = contract Number\n\n(Numeric) add (Numeric) left\n              (Numeric) right =\n  left + right\n\nprint add 2 3  <span class="op">// 5</span>`
  },
  reflection: {
    token: '@', title: 'Reflection',
    copy: '@ returns a non-callable metadata dictionary without invoking the value. Reflection exposes public structure; optional lookup returns missing.',
    code: `<span class="cm">// inspect a callable safely</span>\nadd a b = a + b\nmetadata = @add\n\nprint metadata.kind\nprint metadata.remaining\nprint metadata.absent~\n<span class="op">// Function, 2, ~</span>`
  }
};

const code = document.querySelector('#code-example');
const title = document.querySelector('#example-title');
const copy = document.querySelector('#example-copy');
const token = document.querySelector('#example-token');
const tabs = document.querySelectorAll('[data-tab]');

function setExample(name) {
  const example = examples[name];
  code.innerHTML = example.code;
  title.textContent = example.title;
  copy.textContent = example.copy;
  token.textContent = example.token;
  tabs.forEach(tab => tab.setAttribute('aria-selected', String(tab.dataset.tab === name)));
}

tabs.forEach(tab => tab.addEventListener('click', () => setExample(tab.dataset.tab)));
setExample('functions');

document.querySelector('.copy-button').addEventListener('click', async event => {
  await navigator.clipboard.writeText(code.textContent);
  event.currentTarget.textContent = 'Copied';
  setTimeout(() => event.currentTarget.textContent = 'Copy', 1200);
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

const observer = new IntersectionObserver(entries => {
  entries.forEach(entry => { if (entry.isIntersecting) entry.target.classList.add('visible'); });
}, { threshold: .12 });
document.querySelectorAll('.reveal').forEach(element => observer.observe(element));
