/*
 * Copyright (C) 2023 util2
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.wingate.progressive;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import org.wingate.progressive.core.DrawColor;

/**
 *
 * @author util2
 */
public class SelectionFrame extends javax.swing.JFrame {
    
    private BufferedImage image = null;
    private final String instructions;
    
    private Point pressedInPoint = new Point();    
    private Point releasedInPoint = new Point();

    /**
     * Creates new form SelectionFrame
     */
    public SelectionFrame() {
        initComponents();
        
        // On définit les instructions pour sortir de la fenêtre
        instructions = """
                       Pour valider la sélection, faîtes un clic-droit avec la souris.
                       """;
        
        // On cherche les dimensions de la résolution d'écran
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension screenDimension = toolkit.getScreenSize();
        
        // On définit la taille de la fenêtre en conséquence
        setSize(screenDimension);
        
        // On définit le centrage (au centre)
        setLocationRelativeTo(null);
        
        addMouseListener(new MouseAdapter(){
            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                if(e.getButton() == MouseEvent.BUTTON1){
                    pressedInPoint = e.getLocationOnScreen();
                    releasedInPoint = e.getLocationOnScreen();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
                if(e.getButton() == MouseEvent.BUTTON1){
                    releasedInPoint = e.getLocationOnScreen();
                    repaint();
                }
            }
        });
        
        addMouseMotionListener(new MouseMotionAdapter(){
            @Override
            public void mouseMoved(MouseEvent e) {
                super.mouseMoved(e);
                releasedInPoint = e.getLocationOnScreen();
                repaint();
            }
            
        });
    }

    @Override
    public void paint(Graphics g) {
        if(image != null){        
            BufferedImage img = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);

            Graphics2D g2d = img.createGraphics();
            
            g2d.drawImage(
                    image,
                    0,
                    0,
                    getWidth(),
                    getHeight(),
                    null
            );
            
            Stroke oldStroke = g2d.getStroke();

            g2d.setColor(Color.orange.darker());
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawRect(
                    pressedInPoint.x,
                    pressedInPoint.y,
                    releasedInPoint.x - pressedInPoint.x,
                    releasedInPoint.y - pressedInPoint.y
            );

            g2d.setStroke(oldStroke);
            
            g2d.setColor(DrawColor.black.getColor(0.5f));
            g2d.fillRect(
                    releasedInPoint.x,
                    releasedInPoint.y,
                    700,
                    34
            );
            
            g2d.setColor(Color.yellow);
            Font oldFont = g2d.getFont();
            g2d.setFont(g2d.getFont().deriveFont(24f).deriveFont(Font.BOLD));
            g2d.drawString(
                    instructions,
                    releasedInPoint.x + 20,
                    releasedInPoint.y + 25
            );
            g2d.setFont(oldFont);
            
            g2d.dispose();
            
            g.drawImage(img, 0, 0, null);
        }else{
            super.paint(g);
        }
    }

    public Rectangle getSelectedRegion(){
        return new Rectangle(
                pressedInPoint.x,
                pressedInPoint.y,
                releasedInPoint.x - pressedInPoint.x,
                releasedInPoint.y - pressedInPoint.y
        );
    }

    public void setImage(BufferedImage image) {
        this.image = image;
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 293, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 214, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
