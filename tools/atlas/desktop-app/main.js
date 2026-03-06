const { app, BrowserWindow, dialog, Menu, shell } = require("electron");
const path = require("path");
const fs = require("fs");
const os = require("os");
const net = require("net");
const { spawn } = require("child_process");

const PORT_MIN = 8000;
const PORT_MAX = 8100;

let py = null;
let mainWindow = null;
let currentRepoRoot = null;
let currentPort = 0;

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

async function stopPythonServer() {
  if (!py) return;
  try {
    py.kill();
  } catch {}
  py = null;
}

async function startPythonServer(repoRoot) {
  for (let port = PORT_MIN; port <= PORT_MAX; port++) {
    if (!(await portIsFree(port))) continue;

    const child = spawn("python", ["-m", "http.server", String(port)], {
      cwd: repoRoot,
      stdio: "ignore",
      windowsHide: true,
    });

    const ready = await waitForPort(port, 2500);
    if (ready) {
      LOG(`python server started (pid=${child.pid})`);
      LOG(`port=${port}`);
      return { child, port };
    }

    try {
      child.kill();
    } catch {}
    LOG(`WARN: python server failed to come up on port ${port}, trying next`);
  }

  throw new Error(`Could not start server on ports ${PORT_MIN}-${PORT_MAX}.`);
}

function buildViewerUrl(port, repoRoot, explicitRunId) {
  const runId = explicitRunId || (pickNewestAtlasRun(repoRoot)?.runId ?? null);
  const url = runId
    ? `http://127.0.0.1:${port}/tools/atlas/viewer/index.html?run=${runId}`
    : `http://127.0.0.1:${port}/tools/atlas/viewer/index.html`;

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
    webPreferences: { contextIsolation: true },
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

function runAtlasGenerator() {
  const atlasCmd = path.join(currentRepoRoot, "tools", "atlas", "Atlas.cmd");
  if (!fs.existsSync(atlasCmd)) {
    dialog.showMessageBox({
      type: "error",
      message: `Atlas launcher not found: ${atlasCmd}`,
    });
    return;
  }

  const child = spawn("cmd.exe", ["/c", atlasCmd], {
    cwd: currentRepoRoot,
    detached: true,
    stdio: "ignore",
    windowsHide: true,
  });
  child.unref();
  LOG("Spawned Atlas.cmd from app menu");
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
          label: "Generate new run",
          click: () => {
            runAtlasGenerator();
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
