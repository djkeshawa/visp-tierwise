import { readFile, readdir, mkdir, rm } from 'node:fs/promises';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
const foundationSourceTarget = process.argv.includes('--foundation-source-target');
const modules = [
  ['strata-core', 17],
  ['strata-cli', 17],
  ['strata-spring-boot-starter', 21],
  ['strata-server', 21],
  ['strata-openfeature', 17],
];

function run(args) {
  const result = spawnSync(java, args, { stdio: 'inherit' });
  if (result.error) throw new Error(`Cannot run Java: ${result.error.message}`);
  if (result.status !== 0) throw new Error(`Java command failed (exit ${result.status}).`);
}

async function sources(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = await Promise.all(entries.map(async (entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return sources(path);
    return entry.isFile() && entry.name.endsWith('.java') ? [path] : [];
  }));
  return files.flat().sort();
}

async function build(name, release, jarAvailable, timestamp) {
  const directory = join(root, 'java', name);
  const files = await sources(join(directory, 'src', 'main', 'java'));
  if (files.length === 0) throw new Error(`No Java foundation sources found in ${name}.`);
  if (foundationSourceTarget && files.some((file) => basename(file) !== 'package-info.java')) {
    throw new Error('Foundation source/target mode only accepts package-info.java placeholders.');
  }
  const classes = join(directory, 'target', 'classes');
  await rm(classes, { recursive: true, force: true });
  await mkdir(classes, { recursive: true });
  const releaseOptions = foundationSourceTarget
    ? ['-source', String(release), '-target', String(release)]
    : ['--release', String(release)];
  run(['--module', 'jdk.compiler/com.sun.tools.javac.Main', ...releaseOptions,
    '-encoding', 'UTF-8', '-Xpkginfo:always', '-d', classes, ...files]);
  if (jarAvailable) {
    run(['--module', 'jdk.jartool/sun.tools.jar.Main', '--create', '--file',
      join(directory, 'target', `${name}.jar`), '--date', timestamp, '-C', classes, '.']);
  }
  console.log(`${name}: compiled for Java ${release}${jarAvailable ? ' and packaged' : ''}.`);
}

try {
  if (process.argv.slice(2).some((arg) => arg !== '--foundation-source-target')) {
    throw new Error('Usage: node scripts/build-java.mjs [--foundation-source-target]');
  }
  if (foundationSourceTarget) {
    console.log('Compiling empty package placeholders in source/target mode. Java 17 API compatibility is unverified.');
  }
  const pom = await readFile(join(root, 'java', 'pom.xml'), 'utf8');
  const timestamp = /<project\.build\.outputTimestamp>\s*([^<]+?)\s*<\/project\.build\.outputTimestamp>/.exec(pom)?.[1];
  if (!timestamp) throw new Error('Parent POM must declare project.build.outputTimestamp.');
  run(['--module', 'jdk.compiler/com.sun.tools.javac.Main', '-version']);
  const jarAvailable = spawnSync(java, ['--module', 'jdk.jartool/sun.tools.jar.Main', '--version']).status === 0;
  if (!jarAvailable) console.log('JAR tool unavailable; verifying compiled classes only.');
  for (const [name, release] of modules) await build(name, release, jarAvailable, timestamp);
  console.log('Java foundation build passed. Maven verification is a separate check.');
} catch (error) {
  console.error(`${error.message}\nUse a complete JDK 21+ containing jdk.compiler and ct.sym release signatures. Stripped runtimes cannot verify --release compatibility.`);
  process.exitCode = 1;
}
