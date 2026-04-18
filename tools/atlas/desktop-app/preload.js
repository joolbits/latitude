const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("atlasDesktop", {
  exportRunData: (payload) => ipcRenderer.invoke("atlas-export-run-data", payload),
  getExpectedBiomeIds: () => ipcRenderer.invoke("atlas-get-expected-biome-ids"),
});
