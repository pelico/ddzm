#!/usr/bin/env node
/**
 * ddzm 结构自检（不需要 JDK / Android SDK）。
 *
 * 检查：settings.gradle 模块声明 ↔ 目录 ↔ build.gradle、project() 依赖目标、
 * 依赖图无环、Kotlin package 与目录一致、括号平衡、com.ddzm 内部 import 可解析。
 *
 * 用法：node scripts/verify-structure.mjs   （在仓库根目录执行）
 */
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const errors = [];
const warnings = [];
const read = (rel) => fs.readFileSync(path.join(root, rel), 'utf8');
const exists = (rel) => fs.existsSync(path.join(root, rel));

function walk(dir, out = []) {
  const abs = path.join(root, dir);
  if (!fs.existsSync(abs)) return out;
  for (const entry of fs.readdirSync(abs, { withFileTypes: true })) {
    const rel = path.join(dir, entry.name).split(path.sep).join('/');
    if (entry.isDirectory()) walk(rel, out);
    else if (entry.name.endsWith('.kt')) out.push(rel);
  }
  return out;
}

// ---- 1. 模块声明 ----
const settings = read('settings.gradle');
const modules = [...settings.matchAll(/include\s+['"]:([^'"]+)['"]/g)].map((m) => m[1]);
if (modules.length === 0) errors.push('settings.gradle: no modules declared');
for (const mod of modules) {
  if (!exists(`${mod}/build.gradle`)) errors.push(`module :${mod} has no build.gradle`);
}

// ---- 2. project() 依赖目标 + 依赖图 ----
const graph = new Map();
for (const mod of modules) {
  const deps = [];
  if (exists(`${mod}/build.gradle`)) {
    const text = read(`${mod}/build.gradle`);
    for (const m of text.matchAll(/project\(\s*['"]:([^'"]+)['"]\s*\)/g)) {
      if (!modules.includes(m[1])) errors.push(`:${mod} depends on undeclared module :${m[1]}`);
      deps.push(m[1]);
    }
  }
  graph.set(mod, deps);
}

const visiting = new Set();
const visited = new Set();
function detectCycle(node, stack) {
  if (visiting.has(node)) {
    errors.push(`dependency cycle: ${[...stack, node].join(' -> ')}`);
    return;
  }
  if (visited.has(node)) return;
  visiting.add(node);
  for (const dep of graph.get(node) ?? []) detectCycle(dep, [...stack, node]);
  visiting.delete(node);
  visited.add(node);
}
for (const mod of modules) detectCycle(mod, []);

// ---- 3. Kotlin 源码 ----
// 上游 Olauncher 源码不纳入自检，避免上游更新影响框架判定。
const appFrameworkFiles = [
  'app/src/main/java/app/olauncher/DdzmApplication.kt',
  'app/src/main/java/app/olauncher/AndroidWindowHost.kt',
].filter(exists);
const ktFiles = walk('core')
  .concat(walk('config'), walk('appmodel'), walk('window'), walk('card'), appFrameworkFiles)
  .filter((f, i, arr) => arr.indexOf(f) === i);

const declarations = new Set();
const declRe = /^\s*(?:(?:public|private|internal|protected|abstract|open|final|data|sealed|value|enum|fun|annotation|inner|inline)\s+)*(?:class|interface|object)\s+([A-Za-z_]\w*)/gm;
for (const file of ktFiles) {
  const src = read(file);
  for (const m of src.matchAll(declRe)) declarations.add(m[1]);
}

for (const file of ktFiles) {
  const src = read(file);
  const pkg = src.match(/^package\s+([\w.]+)/m)?.[1];
  const marker = file.includes('/src/test/') ? '/src/test/java/' : '/src/main/java/';
  const expected = file.split(marker)[1]?.split('/').slice(0, -1).join('.');
  if (!pkg) errors.push(`${file}: missing package declaration`);
  else if (pkg !== expected) errors.push(`${file}: package '${pkg}' != path '${expected}'`);

  const stripped = src
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/[^\n]*/g, '')
    .replace(/"(?:\\.|[^"\\])*"/g, '""')
    .replace(/'(?:\\.|[^'\\])*'/g, "''");
  for (const [open, close, label] of [['{', '}', 'braces'], ['(', ')', 'parentheses']]) {
    const a = stripped.split(open).length - 1;
    const b = stripped.split(close).length - 1;
    if (a !== b) errors.push(`${file}: unbalanced ${label} (${a} vs ${b})`);
  }

  for (const m of src.matchAll(/^import\s+([\w.]+)\s*$/gm)) {
    const fqn = m[1];
    if (!fqn.startsWith('com.ddzm.') || fqn.endsWith('.*')) continue;
    const simple = fqn.split('.').pop();
    if (!declarations.has(simple)) {
      errors.push(`${file}: unresolved internal import '${fqn}'`);
    }
  }
}

// ---- 4. 关键装配检查 ----
if (exists('app/src/main/AndroidManifest.xml')) {
  const manifest = read('app/src/main/AndroidManifest.xml');
  if (!manifest.includes('android:name=".DdzmApplication"')) {
    errors.push('Manifest: DdzmApplication not registered');
  }
} else {
  errors.push('Manifest: app/src/main/AndroidManifest.xml missing');
}

// ---- 输出 ----
const summary = {
  modules: modules.length,
  kotlinFiles: ktFiles.length,
  declarations: declarations.size,
  dependencies: Object.fromEntries(graph),
  errors: errors.length,
  warnings: warnings.length,
};
for (const e of errors) console.error(`  ERROR  ${e}`);
for (const w of warnings) console.warn(`  WARN   ${w}`);
console.log(JSON.stringify(summary, null, 2));
if (errors.length > 0) {
  console.error('\nstructure check FAILED');
  process.exit(1);
}
console.log('\nstructure check OK');
