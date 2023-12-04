package org.wingate.progressive;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.EventQueue;

/**
 *
 * @author util2
 */
public class Progressive {

    public static void main(String[] args) {        
        EventQueue.invokeLater(()->{
            FlatLightLaf.setup();
            MainFrame mf = new MainFrame();
            mf.setTitle("Progressive");
            mf.setLocationRelativeTo(null);
            mf.setVisible(true);
        });
    }
}
