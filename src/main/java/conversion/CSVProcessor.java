package conversion;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;

import java.io.*;
import java.util.*;


import javax.swing.*;
import java.awt.*;

import java.util.List;

public class CSVProcessor extends JFrame {

    private JTextField inputFilePathField;
    private JButton browseButton;
    private JButton processButton;
    private JLabel statusLabel;

    public CSVProcessor() {
        setTitle("CSV Processor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(4, 1, 10, 10));
        setSize(400, 200);

        // Input File Selection
        JPanel inputPanel = new JPanel(new BorderLayout(10, 10));
        inputFilePathField = new JTextField();
        browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> selectInputFile());
        inputPanel.add(inputFilePathField, BorderLayout.CENTER);
        inputPanel.add(browseButton, BorderLayout.EAST);

        // Process Button
        processButton = new JButton("Process CSV");
        processButton.addActionListener(e -> processFiles());

        // Status Label
        statusLabel = new JLabel("Select a CSV file to process.", JLabel.CENTER);

        // Add components to the main frame
        add(new JLabel("Input CSV File:", JLabel.CENTER));
        add(inputPanel);
        add(processButton);
        add(statusLabel);

        setLocationRelativeTo(null); // Center the window
        setVisible(true);
    }

    private void selectInputFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            inputFilePathField.setText(selectedFile.getAbsolutePath());
        }
    }

    private void processFiles() {
        String inputFilePath = inputFilePathField.getText().trim();
        if (inputFilePath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an input CSV file.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            String firstOutputFilePath = CSVProcessor.getFirstOutputFilePath();
            String secondOutputFilePath = CSVProcessor.getSecondOutputFilePath();

            // Step 1: Process Initial File
            CSVProcessor.processInitialFile(inputFilePath, firstOutputFilePath);

            // Step 2: Transform Merged File
            CSVProcessor.transformMergedFile(firstOutputFilePath, secondOutputFilePath);

            statusLabel.setText("<html>Processing complete! Files saved to:<br>" +
                    "1. " + firstOutputFilePath + "<br>" +
                    "2. " + secondOutputFilePath + "</html>");
            JOptionPane.showMessageDialog(this, "Processing complete! Files are saved in Downloads folder.", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Processing Error", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("Processing failed. Check input file.");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(CSVProcessor::new);
    }


	public static void processInitialFile(String inputFilePath, String outputFilePath) throws IOException, CsvException {
		List<String[]> inputData = readCSV(inputFilePath);

		List<String[]> mergedData = mergeDescriptions(inputData);
		for (int i = 0; i < mergedData.size(); i++) {
			String[] row = mergedData.get(i);

			// Ensure we don't accidentally trim or alter the period in column 2
			if (row.length > 1) {
				// Column 2 (Name/Description) should not be modified
				row[1] = row[1].trim(); // You may adjust trimming if needed, but do not remove periods.
			}
		}
		splitDescriptionsByQuestionMark(mergedData);

		List<String[]> finalData = addCalculatedColumn(mergedData);

		addHeaders(finalData);

		writeCSV(outputFilePath, finalData);

		System.out.println("Step 1 complete: Merged CSV file created successfully!");
	}

	public static List<String[]> addCalculatedColumn(List<String[]> data) {
		List<String[]> updatedData = new ArrayList<>();

		for (String[] row : data) {
			String[] updatedRow = ensureSixColumns(row);

			String col4 = row[3].trim();
			String col5 = row[4].trim();
			String col6 = "";

			if (isNumeric(col4)) {
				col6 = String.valueOf(Double.parseDouble(col4));
			} else if (isNumeric(col5)) {
				col6 = String.valueOf(-1 * Double.parseDouble(col5));
			} else {
				col6 = "-";
			}

			updatedRow[5] = col6;
			updatedData.add(updatedRow);
		}

		return updatedData;
	}

	public static List<String[]> mergeDescriptions(List<String[]> inputData) {
		List<String[]> processedData = new ArrayList<>();
		StringBuilder descriptionBuilder = new StringBuilder();
		String[] currentRow = null;

		for (int i = 0; i < inputData.size(); i++) {
			String[] row = inputData.get(i);

			if (row.length < 5) {
				row = ensureFiveColumns(row);
			}

			String col2 = row[1].trim();
			String col3 = row[2].trim();
			String col4 = row[3].trim();
			String col5 = row[4].trim();

			if (isNumeric(col2)) {
				if (currentRow != null) {
					currentRow[2] = descriptionBuilder.toString().trim();
					processedData.add(currentRow);
				}
				currentRow = new String[5];
				currentRow[0] = row[0];
				currentRow[1] = col2;
				currentRow[2] = "";
				currentRow[3] = col4;
				currentRow[4] = col5;
				descriptionBuilder.setLength(0);
				descriptionBuilder.append(col3);
			} else if (!col3.isEmpty() || !col4.isEmpty() || !col5.isEmpty()) {
				if (currentRow != null) {
					descriptionBuilder.append(" ").append(col3);
					if (isNumeric(col4)) currentRow[3] = col4;
					if (isNumeric(col5)) currentRow[4] = col5;
				}
			}
			if (containsKeyword(row, "Total", "NET PROFIT", "No. of Accounts", "No. of Entries")) {
				if (currentRow != null) {
					currentRow[2] = descriptionBuilder.toString().trim();
					processedData.add(currentRow);
					currentRow = null;
				}
				processedData.add(row);
			}
		}

		if (currentRow != null) {
			currentRow[2] = descriptionBuilder.toString().trim();
			processedData.add(currentRow);
		}

		return processedData;
	}

	public static void splitDescriptionsByQuestionMark(List<String[]> data) {
		List<String[]> processedData = new ArrayList<>();

		for (String[] row : data) {
			if (row[2] == null || row[2].isEmpty()) {
				processedData.add(row);
				continue;
			}

			String description = row[2];
			String[] parts = description.split("\\?");

			if (parts.length > 2) {
				String[] currentRow = row.clone();
				currentRow[2] = parts[0].trim() + "?";
				processedData.add(currentRow);

				for (int i = 1; i < parts.length; i++) {
					String[] newRow = new String[5];
					newRow[0] = "";
					newRow[1] = "";
					newRow[2] = parts[i].trim();
					newRow[3] = "";
					newRow[4] = "";
					processedData.add(newRow);
				}
			} else {
				processedData.add(row);
			}
		}

		data.clear();
		data.addAll(processedData);
	}

	public static void addHeaders(List<String[]> data) {
		String[] headers = {"Last Year", "Account", "Description", "Debit", "Credit", "Calculated"};
		data.add(0, headers);
	}

	public static void transformMergedFile(String inputFilePath, String outputFilePath) throws IOException, CsvException {
		List<String[]> inputData = readCSV(inputFilePath);

		List<String[]> transformedData = transformData(inputData);

		writeCSV(outputFilePath, transformedData);

		System.out.println("Step 2 complete: Transformed CSV file created successfully!");
	}

	public static List<String[]> transformData(List<String[]> inputData) {
	    List<String[]> transformedData = new ArrayList<>();

	    // Define headers for Transformed_Output.csv
	    String[] newHeaders = {"*Code", "*Name", "*Type", "*Tax Code", "Description", "Dashboard", "Expense Claims", "Enable Payments", "Balance"};
	    transformedData.add(newHeaders);

	    for (int i = 1; i < inputData.size(); i++) { // Skip the header row
	        String[] row = inputData.get(i);

	        // Check if the row contains "Total" in any of its columns; if so, skip it
	        if (containsKeyword(row, "Total", "NET PROFIT", "No. of Accounts", "No. of Entries")) {
	            continue;  // Skip rows that contain the keyword "Total" or other similar keywords
	        }

	        // Ensure that row has at least 9 columns for safe access
	        String[] transformedRow = new String[9];

	        String code = row.length > 1 ? row[1] : ""; // Column 2 of merged data (code)
	        String name = row.length > 2 ? row[2] : ""; // Column 3 of merged data (name)
	        String balance = row.length > 5 ? row[5] : ""; // Column 6 of merged data (balance)

	        if (isNumeric(code)) {
	            // Check if the code is greater than 4 digits
	            if (code.length() > 4) {
	                // Divide the code by 100 and format it to two decimal places
	                double codeValue = Double.parseDouble(code);
	                codeValue /= 100; // Divide by 100
	                code = String.format("%.2f", codeValue); // Format to 2 decimal places
	            }

	            transformedRow[0] = code; // *Code
	            transformedRow[1] = name; // *Name
	            transformedRow[2] = ""; // *Type
	            transformedRow[3] = "BAS Excluded"; // *Tax Code
	            transformedRow[4] = ""; // Description (use *Name for Description)
	            transformedRow[5] = "No"; // Dashboard
	            transformedRow[6] = "No"; // Expense Claims
	            transformedRow[7] = "No"; // Enable Payments
	            transformedRow[8] = balance; // Balance
	        } else {
	            // If Column 1 is not numeric or empty, leave dependent fields blank
	            transformedRow[0] = code; // *Code
	            transformedRow[1] = name; // *Name
	            transformedRow[2] = "";  // *Type
	            transformedRow[3] = "";  // *Tax Code
	            transformedRow[4] = "";  // Description
	            transformedRow[5] = "";  // Dashboard
	            transformedRow[6] = "";  // Expense Claims
	            transformedRow[7] = "";  // Enable Payments
	            transformedRow[8] = "";  // Balance
	        }

	        transformedData.add(transformedRow);
	    }

	    return transformedData;
	}


	public static List<String[]> readCSV(String filePath) throws IOException, CsvException {
		try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
			return reader.readAll();
		}
	}

	public static void writeCSV(String filePath, List<String[]> data) throws IOException {
		try (CSVWriter writer = new CSVWriter(new FileWriter(filePath))) {
			writer.writeAll(data);
		}
	}

	public static boolean isNumeric(String str) {
		if (str == null || str.isEmpty()) return false;
		try {
			Double.parseDouble(str);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public static boolean containsKeyword(String[] row, String... keywords) {
		for (String cell : row) {
			for (String keyword : keywords) {
				if (cell != null && cell.contains(keyword)) {
					return true;
				}
			}
		}
		return false;
	}

	public static String[] ensureSixColumns(String[] row) {
		String[] newRow = new String[6];
		for (int i = 0; i < Math.min(row.length, 6); i++) {
			newRow[i] = row[i];
		}
		return newRow;
	}

	public static String[] ensureFiveColumns(String[] row) {
		String[] newRow = new String[5];
		for (int i = 0; i < Math.min(row.length, 5); i++) {
			newRow[i] = row[i];
		}
		return newRow;
	}

	public static String getInputFilePath() {
		System.out.println("Enter the path to the input CSV file:");
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
			return reader.readLine();
		} catch (IOException e) {
			throw new RuntimeException("Failed to read input file path: " + e.getMessage());
		}
	}

	public static String getFirstOutputFilePath() {
		String userHome = System.getProperty("user.home");
		String downloadsPath = userHome + File.separator + "Downloads";
		String outputFileName = "Merged_Output.csv";
		return downloadsPath + File.separator + outputFileName;
	}

	public static String getSecondOutputFilePath() {
		String userHome = System.getProperty("user.home");
		String downloadsPath = userHome + File.separator + "Downloads";
		String outputFileName = "Transformed_Output.csv";
		return downloadsPath + File.separator + outputFileName;
	}
}