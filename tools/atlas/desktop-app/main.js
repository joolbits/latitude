const { app, BrowserWindow, dialog, Menu, ipcMain, shell } = require("electron");
const path = require("path");
const fs = require("fs");
const os = require("os");
const net = require("net");
const http = require("http");
const { spawn } = require("child_process");

const PORT_MIN = 8000;
const PORT_MAX = 8100;

let py = null;
let mainWindow = null;
let currentRepoRoot = null;
let currentPort = 0;
let atlasProc = null;

function logPath() {
  return path.join(os.tmpdir(), "LatitudeAtlasViewer.log");
}

function log(msg) {
  try {
    fs.appendFileSync(logPath(), `[${new Date().toISOString()}] ${msg}\n`);
  } catch {}
}

const LOG = log;

function getArgValue(name) {
  return getArgValueFromArgs(process.argv, name);
}

function getArgValueFromArgs(args, name) {
  const prefix = `--${name}=`;
  const hit = (args || []).find((a) => a.startsWith(prefix));
  if (!hit) return null;
  let v = hit.slice(prefix.length);
  v = v.replace(/^"(.*)"$/, "$1");
  return v;
}

function configPath() {
  return path.join(app.getPath("userData"), "config.json");
}

function loadSavedRepoRoot() {
  try {
    const p = configPath();
    if (!fs.existsSync(p)) return null;
    const j = JSON.parse(fs.readFileSync(p, "utf8"));
    return typeof j.repoRoot === "string" ? j.repoRoot : null;
  } catch (e) {
    LOG(`WARN: failed to read config.json: ${e?.message || e}`);
    return null;
  }
}

function saveRepoRoot(repoRoot) {
  try {
    fs.mkdirSync(app.getPath("userData"), { recursive: true });
    fs.writeFileSync(configPath(), JSON.stringify({ repoRoot }, null, 2), "utf8");
    LOG(`Saved repoRoot to ${configPath()}`);
  } catch (e) {
    LOG(`WARN: failed to write config.json: ${e?.message || e}`);
  }
}

async function promptForRepoRoot() {
  const res = await dialog.showOpenDialog({
    title: "Select Latitude (Globe) repo folder",
    properties: ["openDirectory"],
    message: "Pick the folder that contains settings.gradle (your Latitude repo root).",
  });
  if (res.canceled || !res.filePaths?.[0]) return null;
  return res.filePaths[0];
}

function findRepoRoot(startDir) {
  let dir = startDir;
  for (let i = 0; i < 12; i++) {
    if (fs.existsSync(path.join(dir, ".git"))) return dir;
    if (fs.existsSync(path.join(dir, "settings.gradle"))) return dir;
    if (fs.existsSync(path.join(dir, "settings.gradle.kts"))) return dir;
    const parent = path.dirname(dir);
    if (parent === dir) break;
    dir = parent;
  }
  return null;
}

function isValidLatitudeRepoRoot(dir) {
  if (!dir) return false;
  try {
    const root = findRepoRoot(dir);
    if (!root) return false;
    const hasGradle =
      fs.existsSync(path.join(root, "settings.gradle")) ||
      fs.existsSync(path.join(root, "settings.gradle.kts"));
    return hasGradle;
  } catch {
    return false;
  }
}

function pickNewestAtlasRun(repoRoot) {
  const runsAbs = path.join(repoRoot, "run-headless", "latdev", "atlas-runs");
  try {
    if (!fs.existsSync(runsAbs)) return null;

    const dirs = fs
      .readdirSync(runsAbs, { withFileTypes: true })
      .filter((d) => d.isDirectory())
      .map((d) => d.name)
      .filter((name) => /^\d{8}-\d{6}$/.test(name))
      .sort()
      .reverse();

    if (!dirs.length) return null;

    const runId = dirs[0];
    const runAbs = path.join(runsAbs, runId);
    const indexAbs = path.join(runAbs, "index.html");
    if (fs.existsSync(indexAbs)) {
      return { runId, urlPath: `/run-headless/latdev/atlas-runs/${runId}/index.html` };
    }

    return { runId, urlPath: `/run-headless/latdev/atlas-runs/${runId}/` };
  } catch (e) {
    LOG(`WARN: pickNewestAtlasRun failed: ${e?.message || e}`);
    return null;
  }
}

async function resolveRepoRoot() {
  LOG(`execPath=${process.execPath}`);
  LOG(`cwd=${process.cwd()}`);
  LOG(`userData=${app.getPath("userData")}`);

  const arg = getArgValue("repoRoot");
  if (arg) {
    const rooted = findRepoRoot(arg);
    if (rooted && isValidLatitudeRepoRoot(rooted)) {
      LOG(`repoRoot from argv: ${rooted}`);
      saveRepoRoot(rooted);
      return rooted;
    }
    LOG(`argv repoRoot invalid: ${arg}`);
  }

  const saved = loadSavedRepoRoot();
  if (saved) {
    const rooted = findRepoRoot(saved);
    if (rooted && isValidLatitudeRepoRoot(rooted)) {
      LOG(`repoRoot from saved config: ${rooted}`);
      return rooted;
    }
    LOG(`saved repoRoot invalid now: ${saved}`);
  }

  const picked = await promptForRepoRoot();
  if (picked) {
    const rooted = findRepoRoot(picked);
    if (rooted && isValidLatitudeRepoRoot(rooted)) {
      LOG(`repoRoot from picker: ${rooted}`);
      saveRepoRoot(rooted);
      return rooted;
    }
    LOG(`picker selection invalid: ${picked}`);
  }

  return null;
}

function portIsFree(port) {
  return new Promise((resolve) => {
    const server = net.createServer();
    server.once("error", () => resolve(false));
    server.once("listening", () => server.close(() => resolve(true)));
    server.listen(port, "127.0.0.1");
  });
}

function waitForPort(port, timeoutMs) {
  return new Promise((resolve) => {
    const start = Date.now();

    const probe = () => {
      const socket = net.createConnection({ host: "127.0.0.1", port }, () => {
        socket.destroy();
        resolve(true);
      });

      socket.on("error", () => {
        socket.destroy();
        if (Date.now() - start >= timeoutMs) {
          resolve(false);
          return;
        }
        setTimeout(probe, 100);
      });
    };

    probe();
  });
}

function checkApiHealth(port, timeoutMs = 2000) {
  return new Promise((resolve) => {
    const req = http.get(
      {
        host: "127.0.0.1",
        port,
        path: "/api/runs",
        timeout: timeoutMs,
      },
      (res) => {
        const ok = res.statusCode === 200;
        res.resume();
        resolve(ok);
      }
    );

    req.on("timeout", () => {
      req.destroy();
      resolve(false);
    });
    req.on("error", () => resolve(false));
  });
}

function sanitizeFolderName(value, fallback = "run") {
  const raw = String(value ?? "").normalize("NFKC").trim();
  if (!raw) return fallback;

  const cleaned = raw
    .replace(/[<>:"/\\|?*\x00-\x1F]+/g, "-")
    .replace(/\s+/g, " ")
    .replace(/[. ]+$/g, "")
    .replace(/^[. ]+/g, "");

  const slug = cleaned.replace(/\s+/g, "_").replace(/-+/g, "-").replace(/_+/g, "_");
  return slug || fallback;
}

function formatTimestampForFolder(date = new Date()) {
  const pad = (n) => String(n).padStart(2, "0");
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
    "-",
    pad(date.getHours()),
    pad(date.getMinutes()),
    pad(date.getSeconds()),
  ].join("");
}

function compactRunId(runId) {
  const raw = String(runId ?? "").trim();
  const normalized = raw.replace(/[^A-Za-z0-9._-]+/g, "");
  return normalized || "run";
}

function isPathInside(child, parent) {
  const rel = path.relative(parent, child);
  return !!rel && !rel.startsWith("..") && !path.isAbsolute(rel);
}

function resolveRunDir(runId) {
  const runsRoot = path.join(currentRepoRoot, "run-headless", "latdev", "atlas-runs");
  const runDir = path.resolve(runsRoot, String(runId || ""));
  const rel = path.relative(runsRoot, runDir);
  if (!rel || rel.startsWith("..") || path.isAbsolute(rel)) return null;
  return fs.existsSync(runDir) ? runDir : null;
}

function copyRecursive(src, dest) {
  if (typeof fs.cpSync === "function") {
    fs.cpSync(src, dest, { recursive: true, force: true });
    return;
  }

  const stat = fs.lstatSync(src);
  if (stat.isDirectory()) {
    fs.mkdirSync(dest, { recursive: true });
    for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
      copyRecursive(path.join(src, entry.name), path.join(dest, entry.name));
    }
    return;
  }

  if (stat.isSymbolicLink()) {
    const linkTarget = fs.readlinkSync(src);
    fs.symlinkSync(linkTarget, dest);
    return;
  }

  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.copyFileSync(src, dest);
}

function buildExportFolderName(runId) {
  const timestamp = formatTimestampForFolder();
  const safeRunId = compactRunId(runId);
  return `LatitudeAtlasExport_${safeRunId}_${timestamp}`;
}

function ensureUniqueExportDir(parentDir, baseName) {
  let candidate = path.join(parentDir, baseName);
  let suffix = 2;
  while (fs.existsSync(candidate)) {
    candidate = path.join(parentDir, `${baseName}-${suffix}`);
    suffix += 1;
  }
  return candidate;
}

function loadExpectedBiomeIdsFromPolicy() {
  if (!currentRepoRoot) {
    return { ok: false, message: "Repository root is not available.", ids: [] };
  }

  const policyPath = path.join(currentRepoRoot, "src", "main", "java", "com", "example", "globe", "dev", "BiomeBandPolicy.java");
  if (!fs.existsSync(policyPath)) {
    return { ok: false, message: `Biome policy source not found: ${policyPath}`, ids: [] };
  }

  try {
    const text = fs.readFileSync(policyPath, "utf8");
    const ids = new Set();
    const matcher = /\b(?:entry|exempt)\("([^"]+)"/g;
    for (const match of text.matchAll(matcher)) {
      const id = String(match[1] || "").trim().toLowerCase();
      if (id) {
        ids.add(id);
      }
    }

    return {
      ok: true,
      source: policyPath,
      ids: Array.from(ids).sort((a, b) => a.localeCompare(b)),
    };
  } catch (e) {
    return {
      ok: false,
      message: `Failed to load biome policy source: ${e?.message || e}`,
      ids: [],
    };
  }
}

async function exportRunDataFromDesktop({ runId, runLabel }) {
  LOG(`atlas-export-run-data invoked for runId=${String(runId || "")}`);
  if (!currentRepoRoot) {
    return { ok: false, message: "Repository root is not available." };
  }

  const runDir = resolveRunDir(runId);
  if (!runDir) {
    return { ok: false, message: "Selected run folder was not found." };
  }

  const picked = await dialog.showOpenDialog({
    title: "Choose export destination folder",
    properties: ["openDirectory", "createDirectory"],
    message: "Pick a folder where Atlas should create the exported run data subfolder.",
  });

  if (picked.canceled || !picked.filePaths?.[0]) {
    return { ok: false, canceled: true, message: "Export canceled." };
  }

  const destinationParent = path.resolve(picked.filePaths[0]);
  if (destinationParent === runDir || isPathInside(destinationParent, runDir)) {
    return {
      ok: false,
      message: "Choose a destination folder outside the selected run folder.",
    };
  }

  fs.mkdirSync(destinationParent, { recursive: true });

  const folderName = buildExportFolderName(runId);
  const exportDir = ensureUniqueExportDir(destinationParent, folderName);

  LOG(`[export] folderName=${folderName}`);
  LOG(`[export] exportDir=${exportDir}`);
  LOG(`[export] exportDir.length=${exportDir.length}`);
  fs.mkdirSync(exportDir, { recursive: true });
  LOG(`[export] exportDir exists before copy/write=${fs.existsSync(exportDir)}`);

  copyRecursive(runDir, exportDir);

  const manifestLines = [
    "Latitude Atlas export",
    `Run ID: ${String(runId || "")}`,
    `Run label: ${String(runLabel || "").trim() || "(not provided)"}`,
    `Source folder: ${runDir}`,
    `Exported at: ${new Date().toString()}`,
    "",
    "This export was copied recursively from the selected headless run folder.",
  ];
  const manifestPath = path.join(exportDir, "export_info.txt");
  fs.mkdirSync(path.dirname(manifestPath), { recursive: true });
  LOG(`[export] manifestPath=${manifestPath}`);
  LOG(`[export] manifestPath.length=${manifestPath.length}`);
  LOG(`[export] manifest parent exists before write=${fs.existsSync(path.dirname(manifestPath))}`);
  fs.writeFileSync(manifestPath, manifestLines.join("\n"), "utf8");

  return {
    ok: true,
    exportDir,
    message: `Exported run data to ${exportDir}`,
  };
}

ipcMain.handle("atlas-get-expected-biome-ids", async () => {
  try {
    LOG("atlas-get-expected-biome-ids invoked");
    return loadExpectedBiomeIdsFromPolicy();
  } catch (e) {
    const message = e?.message || String(e);
    LOG(`WARN: loadExpectedBiomeIdsFromPolicy failed: ${message}`);
    return { ok: false, message: `Expected biome lookup failed: ${message}`, ids: [] };
  }
});

ipcMain.handle("atlas-ping", async () => {
  LOG("atlas-ping invoked");
  return { ok: true, process: "main" };
});

async function stopPythonServer() {
  if (!py) return;
  try {
    py.kill();
  } catch {}
  py = null;
}

async function startPythonServer(repoRoot) {
  const apiServerScript = path.join(repoRoot, "tools", "atlas", "viewer_api_server.py");
  if (!fs.existsSync(apiServerScript)) {
    throw new Error(`Viewer API server script not found: ${apiServerScript}`);
  }

  for (let port = PORT_MIN; port <= PORT_MAX; port++) {
    if (!(await portIsFree(port))) continue;

    const child = spawn("python", [apiServerScript, "--port", String(port)], {
      cwd: repoRoot,
      stdio: "ignore",
      windowsHide: true,
    });

    const ready = await waitForPort(port, 2500);
    const healthy = ready ? await checkApiHealth(port, 2500) : false;
    if (healthy) {
      LOG(`python server started (pid=${child.pid})`);
      LOG(`port=${port}`);
      return { child, port };
    }

    try {
      child.kill();
    } catch {}
    LOG(`WARN: viewer API unhealthy on port ${port}, trying next`);
  }

  throw new Error(`Could not start server on ports ${PORT_MIN}-${PORT_MAX}.`);
}

function buildViewerUrl(port, repoRoot, explicitRunId) {
  const runId = explicitRunId || (pickNewestAtlasRun(repoRoot)?.runId ?? null);
  const url = runId
    ? `http://127.0.0.1:${port}/?run=${runId}`
    : `http://127.0.0.1:${port}/`;

  LOG(`viewerUrl=${url}`);
  if (runId) LOG(`newestRun=${runId}`);
  return url;
}

function ensureMainWindow() {
  if (mainWindow && !mainWindow.isDestroyed()) return mainWindow;

  mainWindow = new BrowserWindow({
    width: 1200,
    height: 900,
    title: "Latitude Atlas Viewer",
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.join(__dirname, "preload.js"),
    },
  });

  mainWindow.webContents.on("did-fail-load", (_e, code, desc, url) => {
    LOG(`did-fail-load code=${code} desc=${desc} url=${url}`);
    mainWindow.setTitle(`Latitude Atlas Viewer (LOAD FAILED: ${code})`);
  });

  mainWindow.webContents.on("did-finish-load", () => {
    LOG("did-finish-load");
    mainWindow.setTitle("Latitude Atlas Viewer");
  });

  return mainWindow;
}

async function loadViewer(explicitRunId) {
  const win = ensureMainWindow();
  const url = buildViewerUrl(currentPort, currentRepoRoot, explicitRunId);
  await win.loadURL(url);
}

async function restartServerAndLoad(explicitRunId) {
  await stopPythonServer();
  const started = await startPythonServer(currentRepoRoot);
  py = started.child;
  currentPort = started.port;
  await loadViewer(explicitRunId);
}

function focusMainWindow() {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.focus();
}

function setGenerateStatus(active, text) {
  try {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.setProgressBar(active ? 2 : -1);
      const js = `window.__latSetGenerationStatus && window.__latSetGenerationStatus(${active ? "true" : "false"}, ${JSON.stringify(text || "")});`;
      mainWindow.webContents.executeJavaScript(js).catch(() => {});
    }
  } catch {}
}

async function changeRepoRootInteractive() {
  const picked = await promptForRepoRoot();
  if (!picked) return;

  const rooted = findRepoRoot(picked);
  if (!rooted || !isValidLatitudeRepoRoot(rooted)) {
    await dialog.showMessageBox({
      type: "error",
      message: "Selected folder is not a valid Latitude repo root.",
    });
    return;
  }

  currentRepoRoot = rooted;
  saveRepoRoot(rooted);
  LOG(`repoRoot changed from menu: ${rooted}`);
  await restartServerAndLoad(null);
}

function openRunsFolder() {
  const runsAbs = path.join(currentRepoRoot, "run-headless", "latdev", "atlas-runs");
  fs.mkdirSync(runsAbs, { recursive: true });
  shell.openPath(runsAbs);
}

function runAtlasGenerator(step = 16) {
  if (atlasProc) {
    dialog.showMessageBox({
      type: "info",
      message: "Atlas generation is already in progress.",
    });
    focusMainWindow();
    return;
  }

  const atlasPs1 = path.join(currentRepoRoot, "tools", "atlas", "Atlas.ps1");
  if (!fs.existsSync(atlasPs1)) {
    dialog.showMessageBox({
      type: "error",
      message: `Atlas launcher not found: ${atlasPs1}`,
    });
    return;
  }

  setGenerateStatus(true, `Generating atlas run (step ${step})…`);

  const childEnv = { ...process.env };
  // Strip inherited JVM debug flags that pollute spawned Gradle/Java processes.
  delete childEnv.JAVA_TOOL_OPTIONS;
  delete childEnv._JAVA_OPTIONS;
  if (process.platform === "win32") {
    const windowsJdk = "C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.10.7-hotspot";
    if (fs.existsSync(path.join(windowsJdk, "bin", "java.exe"))) {
      // Set plain paths — no extra quotes. JAVA_HOME + ORG_GRADLE_JAVA_HOME are
      // sufficient for Gradle to pick the right JDK without GRADLE_OPTS quoting
      // issues (which split on the space in "Program Files").
      childEnv.JAVA_HOME = windowsJdk;
      childEnv.ORG_GRADLE_JAVA_HOME = windowsJdk;
    }
  }

  const child = spawn(
    "powershell.exe",
    ["-ExecutionPolicy", "Bypass", "-File", atlasPs1, "-Step", String(step), "-NoViewerOpen"],
    {
      cwd: currentRepoRoot,
      env: childEnv,
      detached: false,
      stdio: "ignore",
      windowsHide: true,
    }
  );
  atlasProc = child;
  LOG(`Spawned Atlas.ps1 from app menu (step=${step})`);
  showGenerationStartedToast(step);

  child.once("exit", async (code) => {
    atlasProc = null;
    setGenerateStatus(false, "");
    LOG(`Atlas.ps1 exited with code=${code}`);
    showGenerationFinishedToast(step, code === 0);
    if (code === 0) {
      await restartServerAndLoad(null).catch((e) => {
        LOG(`WARN: reload after generation failed: ${e?.message || e}`);
      });
    }
  });

  child.once("error", (e) => {
    atlasProc = null;
    setGenerateStatus(false, "");
    LOG(`Atlas.ps1 spawn error: ${e?.message || e}`);
    dialog.showMessageBox({
      type: "error",
      message: `Failed to start Atlas generation: ${e?.message || e}`,
    });
  });
}

function showGenerationStartedToast(step) {
  try {
    const js = `window.__latSetGenerationStatus && window.__latSetGenerationStatus(true, "Generating atlas run (step ${step})…");`;
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.executeJavaScript(js).catch(() => {});
    }
  } catch {}
}

function showGenerationFinishedToast(step, ok) {
  try {
    const message = ok
      ? `Generation complete (step ${step}).`
      : `Generation failed (step ${step}). Check Help -> Open log file.`;
    const js = `
      if (window.__latSetGenerationStatus) window.__latSetGenerationStatus(false, "");
      const el = document.getElementById("status-toast");
      if (el && el.querySelector(".msg")) {
        const dot = el.querySelector(".dot");
        const msg = el.querySelector(".msg");
        if (dot) dot.className = "dot ${ok ? "success" : "error"}";
        msg.textContent = ${JSON.stringify(message)};
        el.classList.add("visible");
        setTimeout(() => el.classList.remove("visible"), 2600);
      }
    `;
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.executeJavaScript(js).catch(() => {});
    }
  } catch {}
}

function openLogFile() {
  try {
    if (!fs.existsSync(logPath())) fs.writeFileSync(logPath(), "", "utf8");
  } catch {}
  shell.openPath(logPath());
}

function buildMenu() {
  const template = [
    {
      label: "Atlas",
      submenu: [
        {
          label: "Generate new run (Step 16)",
          click: () => {
            runAtlasGenerator(16);
          },
        },
        {
          label: "Generate new run (Step 32)",
          click: () => {
            runAtlasGenerator(32);
          },
        },
        {
          label: "Open runs folder",
          click: () => {
            openRunsFolder();
          },
        },
        {
          label: "Change repo root",
          click: async () => {
            await changeRepoRootInteractive();
          },
        },
      ],
    },
    {
      label: "Help",
      submenu: [
        {
          label: "Open log file",
          click: () => {
            openLogFile();
          },
        },
      ],
    },
  ];

  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

async function handleSecondInstance(argv) {
  focusMainWindow();

  const runArg = getArgValueFromArgs(argv, "run") || getArgValueFromArgs(argv, "runId");
  const repoArg = getArgValueFromArgs(argv, "repoRoot");

  if (repoArg) {
    const rooted = findRepoRoot(repoArg);
    if (rooted && isValidLatitudeRepoRoot(rooted)) {
      currentRepoRoot = rooted;
      saveRepoRoot(rooted);
      LOG(`repoRoot from second-instance argv: ${rooted}`);
      await restartServerAndLoad(runArg || null);
      return;
    }
    LOG(`second-instance repoRoot invalid: ${repoArg}`);
  }

  if (runArg && /^\d{8}-\d{6}$/.test(runArg)) {
    await loadViewer(runArg);
  }
}

ipcMain.handle("atlas-export-run-data", async (_event, payload = {}) => {
  try {
    return await exportRunDataFromDesktop(payload || {});
  } catch (e) {
    const message = e?.message || String(e);
    LOG(`WARN: exportRunDataFromDesktop failed: ${message}`);
    return { ok: false, message: `Export failed: ${message}` };
  }
});

const singleInstance = app.requestSingleInstanceLock();
if (!singleInstance) {
  app.quit();
} else {
  app.on("second-instance", (_event, argv) => {
    handleSecondInstance(argv).catch((e) => {
      LOG(`WARN: second-instance handling failed: ${e?.message || e}`);
    });
  });

  app.whenReady().then(async () => {
    try {
      const repoRoot = await resolveRepoRoot();
      LOG(`repoRoot=${repoRoot}`);
      if (!repoRoot) {
        throw new Error("Could not locate repo root (.git or settings.gradle) - user canceled or invalid folder.");
      }

      currentRepoRoot = repoRoot;
      buildMenu();
      ensureMainWindow();

      const runArg = getArgValue("run") || getArgValue("runId");
      await restartServerAndLoad(runArg || null);
    } catch (e) {
      LOG(`FATAL: ${e && e.stack ? e.stack : String(e)}`);
      app.quit();
    }
  });
}

app.on("window-all-closed", () => {
  stopPythonServer().finally(() => app.quit());
});
