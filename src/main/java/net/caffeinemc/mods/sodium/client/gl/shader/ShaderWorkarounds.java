// Copyright 2020 Grondag
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package net.caffeinemc.mods.sodium.client.gl.shader;

import org.lwjgl.opengl.GL20;

/**
 * Contains a workaround for a crash in nglShaderSource on some AMD drivers. Copied from
 * <a href="https://github.com/grondag/canvas/commit/820bf754092ccaf8d0c169620c2ff575722d7d96">this Canvas commit</a>
 *
 * <p>The reference implements this workaround via {@code GL20C.nglShaderSource} with a null
 * length pointer (forcing the driver to rely on the null terminator). LWJGL 2 has no native
 * call surface for that, so the port uses the {@code CharSequence} overload of
 * {@link GL20#glShaderSource(int, CharSequence)} — the same path vanilla 1.8.9 uses — which
 * performs the null-termination itself.</p>
 */
class ShaderWorkarounds {
    static void safeShaderSource(int glId, CharSequence source) {
        GL20.glShaderSource(glId, source);
    }
}
