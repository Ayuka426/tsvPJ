import java.io.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TSVProcessor {

    /**
     * 【メイン関数】
     * コマンドライン引数で実行モード（normalize(第一正規化) / group（第一正規化の逆変換））と入力ファイル名を指定して実行
     * 出力ファイル名は自動生成
     *
     * 実行コマンド例：
     *   java TSVProcessor normalize input.tsv
     *   java TSVProcessor group input.tsv
     */

    public static void main(String[] args) {
        // 引数チェック
        if (args.length < 2) {
            System.out.println("使用方法: java TSVProcessor <モード> <入力ファイル>");
            System.out.println("モード指定:");
            System.out.println("  normalize : 非正規化データを正規化します");
            System.out.println("  group     : 正規化済みデータをグループ化します");
            return;
        }

        String mode = args[0];
        String inputPath = args[1];

        //文字チェック（関数を使用）
        //ASCII印字可能文字のみが使用されていることを確認
        try {
            validateAsciiPrintable(inputPath);
        } catch (Exception e) {
            System.err.println("エラー: ファイルにASCII印字可能文字以外が含まれています:");
            e.printStackTrace();

            return; // チェック失敗なら処理を中止
        }

        // 出力ファイル名を自動生成
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
        String outputPath = timestamp + "processed.tsv";

        // 入力されたコマンドからモードを判定して処理を実行
        try {
            switch (mode.toLowerCase()) {
                case "normalize":
                    System.out.println("モード: 正規化を実行します。");
                    normalizeFile(inputPath, outputPath);
                    break;

                case "group":
                    System.out.println("モード: グループ化を実行します。");
                    processNormalizedFile(inputPath, outputPath);
                    break;

                default:
                    System.out.println("エラー: 不明なモード '" + mode + "'");
                    System.out.println("使用可能なモード: normalize / group");
                    return;
            }

            System.out.println("出力ファイル: " + outputPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    /**
     * 【文字チェック関数】
     * ファイル内の全ての文字がASCII印字可能文字かをチェック
     * メイン関数内で使用
     * （ASCII印字可能範囲は 0x20 to 0x7E）
     */
    private static void validateAsciiPrintable(String inputPath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(inputPath))) {
            String line;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;

                //タブもしくはASCII印字可能範囲(0x20～0x7E)以外かどうか1文字ずつチェック
                for (char ch : line.toCharArray()) {
                    if (!(ch == '\t' || (ch >= 0x20 && ch <= 0x7E))) {
                        throw new IllegalArgumentException(
                            String.format("エラー: 非ASCII文字を検出しました（行 %d, 文字: '%s', コード: U+%04X）", 
                                          lineNum, ch, (int) ch)
                        );
                    }
                }
            }
        }
    }



    /**
     * 【1. 第一正規化の処理（normalizeFile）】
     *
     * タブ区切りのデータを読み込み、各セル内のコロン区切りを展開し
     * 全ての値の組み合わせを出力
     */
    public static void normalizeFile(String inputPath, String outputPath) throws Exception {
        File outputFile = new File(outputPath);

        try (
            BufferedReader reader = new BufferedReader(new FileReader(inputPath));
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))
        ) {
            String line;
            int lineNum = 0;
            int expectedColumns = -1; // 列数確認用

            // 行ごとに読み込み、各セルの全組み合わせを作成する
            while ((line = reader.readLine()) != null) {
                lineNum++;

                if (line.trim().isEmpty()) {
                    continue;
                }

                // 行をセルごとに分割（タブ区切り）
                String[] cells = line.split("\t", -1);

                // 列数が5を超えていないかチェック
                if (cells.length > 5) {
                    throw new IllegalArgumentException(
                        String.format("エラー: 列数超過 %d行目の列数が%d列です（最大5列まで）", lineNum, cells.length)
                    );
                }

                // 1行目で列数を固定（仕様より、データ内の列数は全ての行で同じ想定）
                if (expectedColumns == -1) {
                    expectedColumns = cells.length;
                }

                // 列数が同じかチェック
                if (cells.length != expectedColumns) {
                    throw new IllegalArgumentException("エラー: 列数不一致 " + lineNum + "行目（期待: " + expectedColumns + "列, 実際: " + cells.length + "列）");
                }

                // 各セルをコロン区切りで展開し配列に追加
                List<List<String>> splitCells = new ArrayList<>();
                int colIndex = 0;

                for (String cell : cells) {
                    colIndex++;

                    // ▼ 仕様チェック ▼
                    // セル文字数（0〜10000文字）
                    if (cell.length() > 10000) {
                        throw new IllegalArgumentException(
                            String.format("エラー: セル文字数超過 %d行目 %d列目 のセルが10000文字を超えています（%d文字）",
                                        lineNum, colIndex, cell.length())
                        );
                    }

                    // コロン区切りの値数（最大10個）
                    String[] values = cell.split(":");
                    if (values.length > 10) {
                        throw new IllegalArgumentException(
                            String.format("エラー: 値数超過 %d行目 %d列目 の値が%d個あります（最大10個まで）",
                                        lineNum, colIndex, values.length)
                        );
                    }

                    splitCells.add(Arrays.asList(values));
                }

                // デカルト積（処理している行の全組み合わせ）を生成（下記に記述するcartesianProduct関数を使用）
                List<List<String>> combinations = cartesianProduct(splitCells);

                // 生成したデカルト積を組み合わせごとに出力
                for (List<String> combo : combinations) {
                    writer.write(String.join("\t", combo));
                    writer.newLine();
                }
            }

            System.out.println("正規化が完了しました。");

        } catch (Exception e) {
            System.err.println(e.getMessage());
            if (outputFile.exists()) outputFile.delete();
            throw e;
        }
    }

    /**
     * 【全組み合わせ（デカルト積）を生成する関数】
     * normalizeFile関数内で使用
     *
     * 例：
     *   入力: [ ["apple"], ["fruit", "sale"] ]
     *   出力: [ ["apple", "fruit"], ["apple", "sale"] ]
     *
    */
    private static List<List<String>> cartesianProduct(List<List<String>> lists) {
        List<List<String>> result = new ArrayList<>();
        result.add(new ArrayList<>());

        // 各セルを順に処理
        for (List<String> list : lists) { 

            // 新しい組み合わせを格納する一時リストを作成
            List<List<String>> newResult = new ArrayList<>();

            // これまでの組み合わせ(result)と、今回のセル内の値(list)を組み合わせる
            for (List<String> combination : result) {
                for (String value : list) {
                    // これまでの組み合わせをコピーし、新しい（今回の）値を追加
                    List<String> newCombo = new ArrayList<>(combination);
                    newCombo.add(value);

                    // 新しい組み合わせをnewResultに追加
                    newResult.add(newCombo);
                }
            }

            // 今回の組み合わせをresultに格納
            result = newResult;
        }

        //行内のすべてのセルを処理し終わったらreturnで全組み合わせを返す
        return result;
    }

    /**
     * 【2. 第一正規化の逆変換の処理（processNormalizedFile）】
     *
     * 正規化されているファイルにて、
     * 同じキーを持つ行をまとめて、値をコロン(:)で連結する
     */
    public static void processNormalizedFile(String inputPath, String outputPath) throws Exception  {
        File outputFile = new File(outputPath);

        // 1つのキーに対して複数の値が紐づけられるため、LinkedHashMapを使用（順序を保持）
        Map<String, List<String>> groupedData = new LinkedHashMap<>(); 

        try (
            BufferedReader reader = new BufferedReader(new FileReader(inputPath));
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))
        ) {
            String line;
            int lineNum = 0;

            // 行ごとに読み込み
            while ((line = reader.readLine()) != null) {
                lineNum++;

                // 行数チェック
                if (lineNum > 1000) {
                    throw new IllegalArgumentException("エラー: 行数が上限（1000行）を超えています。");
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                // 行をセルごとに分割（タブ区切り）
                String[] cols = line.split("\t", -1);

                // 列数チェック
                if (cols.length != 2) {
                    throw new IllegalArgumentException("エラー: 不正な列数: " + lineNum + "行目");
                }

                String key = cols[0];
                String value = cols[1];

                // 各セルの文字数チェック（0〜100文字）
                if (key.length() > 100) {
                    throw new IllegalArgumentException("エラー: キーが100文字を超えています（行 " + lineNum + "）。");
                }
                if (value.length() > 100) {
                    throw new IllegalArgumentException("エラー: 値が100文字を超えています（行 " + lineNum + "）。");
                }

                //該当キーが存在しなければリストに追加
                groupedData.computeIfAbsent(key, k -> new ArrayList<>()).add(value);

                // 追加後に値の個数チェック
                if (groupedData.get(key).size() > 10) {
                    throw new IllegalArgumentException("エラー: キー '" + key + "' に紐づく値が10個を超えています。");
                }
            }

            //各キーごとに処理。
            //キーに対応する値を:で区切り、キー\t値1:値2…となるように行を生成する
            for (Map.Entry<String, List<String>> entry : groupedData.entrySet()) {
                String key = entry.getKey();
                String joined = String.join(":", entry.getValue());
                writer.write(key + "\t" + joined);
                writer.newLine();
            }

            System.out.println("グループ化が完了しました。");

        } catch (Exception e) {
            System.err.println(e.getMessage());
            if (outputFile.exists()) outputFile.delete();
            throw e;
        }
    }


}
