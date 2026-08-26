import {defineConfig} from 'vite';
import {crx, type ManifestV3Export} from '@crxjs/vite-plugin';
import manifest from './manifest.json';

export default defineConfig({
    plugins: [crx({manifest: manifest as ManifestV3Export})],
});
