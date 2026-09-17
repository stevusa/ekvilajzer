package rs.tvbox210.equalizer;

import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

final class ShizukuSessionScanner {
    private ShizukuSessionScanner() { }

    static boolean isReady() {
        try {
            return Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static List<Integer> findActiveSessions() throws Exception {
        if (!Shizuku.pingBinder()) {
            throw new IllegalStateException("Shizuku nije pokrenut");
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Shizuku dozvola nije odobrena");
        }

        String command = "dumpsys media.audio_flinger | awk '$2==\"yes\" && $4>0 {print $4}' | sort -nu";
        Method method = Shizuku.class.getDeclaredMethod(
                "newProcess", String[].class, String[].class, String.class);
        method.setAccessible(true);

        ShizukuRemoteProcess process = null;
        Set<Integer> sessions = new LinkedHashSet<>();
        try {
            String[] cmd = new String[]{"sh", "-c", command};
            process = (ShizukuRemoteProcess) method.invoke(null, (Object) cmd, null, null);

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0) continue;
                try {
                    int session = Integer.parseInt(line);
                    if (session > 0 && session < 1000000) sessions.add(session);
                } catch (NumberFormatException ignored) { }
            }

            try {
                BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                while (err.readLine() != null) { }
            } catch (Throwable ignored) { }

            try { process.waitFor(); } catch (Throwable ignored) { }
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Throwable ignored) { }
            }
        }

        ArrayList<Integer> result = new ArrayList<>(sessions);
        Collections.sort(result);
        return result;
    }
}
