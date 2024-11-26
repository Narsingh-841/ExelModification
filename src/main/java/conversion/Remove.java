package conversion;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class Remove {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Launch the GUI
            new Remove().initGui();
        });
    }

    private void initGui() {
        JFrame frame = new JFrame("CSV Cleaner");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 200);
        frame.setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, 1));

        JLabel label = new JLabel("Select a CSV file to process:", SwingConstants.CENTER);
        panel.add(label);

        JButton selectFileButton = new JButton("Select Input File");
        selectFileButton.addActionListener(e -> {
            // File chooser for input CSV
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select Input CSV File");
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV Files", "csv"));

            int fileSelectionResult = fileChooser.showOpenDialog(frame);

            if (fileSelectionResult == JFileChooser.APPROVE_OPTION) {
                File inputFile = fileChooser.getSelectedFile();

                JFileChooser saveChooser = new JFileChooser();
                saveChooser.setDialogTitle("Select Output Location");
                saveChooser.setSelectedFile(new File("cleaned_output.csv"));

                int saveSelectionResult = saveChooser.showSaveDialog(frame);
                if (saveSelectionResult == JFileChooser.APPROVE_OPTION) {
                    Path outputFilePath = saveChooser.getSelectedFile().toPath();
                    processCsvFile(inputFile, outputFilePath);
                    JOptionPane.showMessageDialog(frame, "Processed file saved at: " + outputFilePath);
                }
            }
        });
        panel.add(selectFileButton);

        JButton exitButton = new JButton("Exit");
        exitButton.addActionListener(e -> System.exit(0));
        panel.add(exitButton);

        frame.add(panel, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private static void processCsvFile(File inputFile, Path outputFilePath) {
        List<List<String>> allRows = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath.toFile()))) {

            String line;
            List<String> headerRow = null;

            // Read all rows from the input CSV file
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue; // Skip empty lines

                // Split the row into columns (handle commas inside quotes)
                String[] columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                // Add the row to allRows list
                allRows.add(new ArrayList<>(List.of(columns)));
            }

            // Determine the maximum number of columns in any row
            int maxColumns = allRows.stream()
                    .mapToInt(row -> row.size())
                    .max()
                    .orElse(0);

            // Process each column to shift empty cells upwards
            for (int colIndex = 0; colIndex < maxColumns; colIndex++) {
                List<String> columnData = new ArrayList<>();

                // Collect all non-empty values from this column
                for (List<String> row : allRows) {
                    if (colIndex < row.size() && !row.get(colIndex).trim().isEmpty()) {
                        columnData.add(row.get(colIndex).trim());
                    }
                }

                // Now, fill the original rows with the non-empty values in this column, shifting upwards
                for (int rowIndex = 0; rowIndex < allRows.size(); rowIndex++) {
                    List<String> row = allRows.get(rowIndex);

                    // Ensure the row is large enough to handle this column
                    while (row.size() <= colIndex) {
                        row.add(""); // Add empty cell if the row is smaller
                    }

                    // Fill the cell with a value from columnData or leave it empty if columnData is exhausted
                    if (rowIndex < columnData.size()) {
                        row.set(colIndex, columnData.get(rowIndex)); // Place non-empty values
                    } else {
                        row.set(colIndex, ""); // Place an empty value if columnData is exhausted
                    }
                }
            }

            // Write the processed rows to the output file
            for (List<String> row : allRows) {
                writer.write(String.join(",", row));
                writer.newLine();
            }

            System.out.println("Processed file saved to: " + outputFilePath);
        } catch (IOException e) {
            System.err.println("Error while processing the CSV file: " + e.getMessage());
        }
    }

}
