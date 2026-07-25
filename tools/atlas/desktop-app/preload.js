const { contextBridge, ipcRenderer } = require("electron");

const atlasDesktop = {
  exportRunData: (payload) => ipcRenderer.invoke("atlas-export-run-data", payload),
  getExpectedBiomeIds: () => ipcRenderer.invoke("atlas-get-expected-biome-ids"),
  ping: () => ipcRenderer.invoke("atlas-ping"),
};

contextBridge.exposeInMainWorld("atlasDesktop", atlasDesktop);
console.log("[atlas] preload exposed atlasDesktop keys:", Object.keys(atlasDesktop).join(", "));
