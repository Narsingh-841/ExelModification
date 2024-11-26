package conversion;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

public class CsvProcessor2 {

    double closingBalance;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Display the GUI for file selection and output location
            new CsvProcessor2().initGui();
        });
    }

    private void initGui() {
        JFrame frame = new JFrame("CSV Processor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 200);
        frame.setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, 1));

        JLabel label = new JLabel("Select a CSV or XLS file to process:", SwingConstants.CENTER);
        panel.add(label);

        JButton selectFileButton = new JButton("Select Input File");
        selectFileButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select Input File");
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV and Excel Files", "csv", "xls"));

            int fileSelectionResult = fileChooser.showOpenDialog(frame);

            if (fileSelectionResult == JFileChooser.APPROVE_OPTION) {
                File inputFile = fileChooser.getSelectedFile();
                String inputFilePath = inputFile.getAbsolutePath();

                String csvFilePath = inputFilePath;
                if (inputFilePath.endsWith(".xls")) {
                    try {
                        csvFilePath = convertXlsToCsv(inputFile);
                        JOptionPane.showMessageDialog(frame, "Converted .xls file to .csv: " + csvFilePath);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(frame, "Error converting .xls to .csv: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }

                JFileChooser saveChooser = new JFileChooser();
                saveChooser.setDialogTitle("Select Output Location");
                saveChooser.setSelectedFile(new File("processed_output.csv"));

                int saveSelectionResult = saveChooser.showSaveDialog(frame);
                if (saveSelectionResult == JFileChooser.APPROVE_OPTION) {
                    Path outputFilePath = saveChooser.getSelectedFile().toPath();
                    processCsvFile(new File(csvFilePath), outputFilePath);
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


    private static String convertXlsToCsv(File xlsFile) throws IOException {
        String csvFilePath = xlsFile.getAbsolutePath().replace(".xls", ".csv");
        File csvFile = new File(csvFilePath);

        try (Workbook workbook = new HSSFWorkbook(new FileInputStream(xlsFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {

            Sheet sheet = workbook.getSheetAt(0);

            int maxColumns = 0;
            for (Row row : sheet) {
                maxColumns = Math.max(maxColumns, row.getLastCellNum());
            }

            for (Row row : sheet) {
                StringBuilder rowBuilder = new StringBuilder();

                for (int colIndex = 0; colIndex < maxColumns; colIndex++) {
                    Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String cellValue = (cell == null) ? "" : getFormattedCellValue(cell);
                    rowBuilder.append(cellValue).append(",");
                }

                if (rowBuilder.length() > 0) {
                    rowBuilder.setLength(rowBuilder.length() - 1);
                }
                writer.write(rowBuilder.toString());
                writer.newLine();
            }
        }
        return csvFilePath;
    }

    private static String getFormattedCellValue(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new java.text.SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    private static void processCsvFile(File inputFile, Path outputFilePath) {
        List<List<String>> allRows = new ArrayList<>();
        double closingBalance;
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath.toFile()))) {

            writer.write("*AssetName,*AssetNumber,PurchaseDate,PurchasePrice,AssetType,Description,TrackingCategory1,TrackingOption1,TrackingCategory2,TrackingOption2,SerialNumber,WarrantyExpiry,Book_DepreciationStartDate,Book_CostLimit,Book_ResidualValue,Book_DepreciationMethod,Book_AveragingMethod,Book_Rate,Book_EffectiveLife,Book_OpeningBookAccumulatedDepreciation,Tax_DepreciationMethod,Tax_PoolName,Tax_PooledDate,Tax_PooledAmount,Tax_DepreciationStartDate,Tax_CostLimit,Tax_ResidualValue,Tax_AveragingMethod,Tax_Rate,Tax_EffectiveLife,Tax_OpeningAccumulatedDepreciation");
            writer.newLine();

            String line;
            String previousTextInCol4 = null;
            int lineNumber = 0;

            String bookAveragingMethod = "Actual Days";
            String taxRate = "Actual Days";

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                lineNumber++;

                if (lineNumber <= 16) {
                    continue;
                }

                if (line.isEmpty()) continue;

                if (line.contains("TOTAL")) {
                    break;
                }

                String[] columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                for (int i = 18; i < columns.length; i++) {
                    columns[i] = columns[i].replaceAll("^\"|\"$", "").trim();
                }

                String col1 = (columns.length > 0) ? columns[0] : "";
                String col2 = (columns.length > 1) ? columns[1] : "";
                String col4 = (columns.length > 3) ? columns[3] : "";
                String Book_Rate = (columns.length > 17) ? columns[17] : "";
                String colHStr = (columns.length > 13) ? columns[13] : "";
                double colH = parseValue(colHStr.replaceAll("[^\\d.]", "").trim());
                col2 = col2.replaceAll("\\s*::\\s*AM", "").trim();

                String col4Text = "";
                String col4Number = "";
                String col6 = "";

                col4Text = col4.trim();  

                col4Text = col4Text.replaceAll("[^\\x00-\\x7F]", "");
                col4Text = col4Text.replaceAll("\\.", "");
                col4Number = "";
                if (col4.matches(".*\\d+\\.\\d+.*")) {
                    col4Number = col4.replaceAll("[^\\d.]", "").trim();
                    col4Text = col4.replaceAll("[^\\D]", "").trim();
                }
                double col4Value = col4Number.isEmpty() ? 0.0 : Double.parseDouble(col4Number);
                closingBalance = colH - col4Value;

                previousTextInCol4 = col4Text;

                String col13 = col2;
                String col25 = col2;

                String assetName = col4Text;
                String purchaseDate = col2;
                String assetNumber = col1;
                String assetType = col6;
                String book_DepreciationStartDate = col13;
                String tax_DepreciationStartDate = col25;
                String tax_Rate= Book_Rate;
                closingBalance = col4Value - colH;
                String closingBalanceStr = (closingBalance == 0.0) ? "" : String.valueOf(closingBalance);

                assetName = assetName.replaceAll("\\.", "");

                bookAveragingMethod = assetNumber.isEmpty() ? "" : "Actual Days";
                taxRate = assetNumber.isEmpty() ? "" : "Actual Days";  // Don't set "Actual Days" if assetNumber is empty

                String[] outputRow = {
                        !assetName.isEmpty() ? assetName : "",
                        !assetNumber.isEmpty() ? assetNumber : "",
                        !purchaseDate.isEmpty() ? purchaseDate : "",
                        !col4Number.isEmpty() ? col4Number : "",
                        !assetType.isEmpty() ? assetType : "",
                        "", "", "", "", "", "", "", 
                        !book_DepreciationStartDate.isEmpty() ? book_DepreciationStartDate : "",
                        "", "", "", !bookAveragingMethod.isEmpty() ? bookAveragingMethod : "",
                        !Book_Rate.isEmpty() ? Book_Rate : "", "", !closingBalanceStr.isEmpty() ? closingBalanceStr : "",
                        "", "", "", "", !tax_DepreciationStartDate.isEmpty() ? tax_DepreciationStartDate : "",
                        "", "", !taxRate.isEmpty() ? taxRate : "", !Book_Rate.isEmpty() ? Book_Rate : "",
                        !closingBalanceStr.isEmpty() ? closingBalanceStr : "", "", "", "", ""
                };
                allRows.add(Arrays.asList(outputRow));
                writer.write(String.join(",", outputRow));
                writer.newLine();
               

            }
            // Write the row if it contains any non-empty cells
           
       

            System.out.println("Processed output file has been created at: " + outputFilePath);
        } catch (IOException e) {
            System.err.println("Error while processing the file: " + e.getMessage());
        }
    }



    private static double parseValue(String value) {
        try {
            // Check if the string is empty or just spaces
            if (value == null || value.trim().isEmpty()) {
                // Handle empty case, return default value like 0.0
                System.out.println("Empty string encountered. Defaulting to 0.");
                return 0.0;  // Returning default value
            }

            // Otherwise, safely parse the double value
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            // Handle the case where parsing fails (e.g., non-numeric input)
            System.out.println("Invalid number format: " + value);
            return 0.0;  // Return a default value in case of an error
        }
    }

}
