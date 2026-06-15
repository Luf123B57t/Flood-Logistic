package com.aidsight.domain.processor.impl;

import com.aidsight.domain.model.instance.NewspaperArticleInstance;
import com.aidsight.domain.model.task.DamageAssessmentTask;
import com.aidsight.domain.processor.Processor;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processor implementation for damage assessment tasks on newspaper article instances.
 * <p>
 * This processor analyzes Vietnamese newspaper articles to extract damage information
 * by province. It uses pattern matching to identify provinces mentioned in the text
 * and extract associated damage statistics including casualties, property damage,
 * agricultural losses, and infrastructure damage.
 * </p>
 * <p>
 * The processor supports extraction of the following damage types:
 * </p>
 * <ul>
 * <li>Deaths, missing persons, and injuries</li>
 * <li>Damaged and flooded houses</li>
 * <li>Agricultural damage (rice, crops, fruit trees)</li>
 * <li>Livestock losses (cattle, poultry)</li>
 * <li>Aquaculture damage</li>
 * <li>Infrastructure damage (electric poles)</li>
 * </ul>
 *
 * @see DamageAssessmentTask
 * @see NewspaperArticleInstance
 */
public class DamageAssessmentNewspaperArticleProcessor implements Processor<DamageAssessmentTask, NewspaperArticleInstance, DamageAssessmentTask.Result> {

    // List of 63 Vietnamese provinces
    private static final List<String> VIETNAM_PROVINCES = Arrays.asList(
            "Hà Nội", "Hồ Chí Minh", "Đà Nẵng", "Hải Phòng", "Cần Thơ",
            "Hà Giang", "Cao Bằng", "Bắc Kạn", "Tuyên Quang", "Lào Cai",
            "Điện Biên", "Lai Châu", "Sơn La", "Yên Bái", "Hòa Bình",
            "Thái Nguyên", "Lạng Sơn", "Quảng Ninh", "Bắc Giang", "Phú Thọ",
            "Vĩnh Phúc", "Bắc Ninh", "Hải Dương", "Hưng Yên", "Thái Bình",
            "Hà Nam", "Nam Định", "Ninh Bình",
            "Thanh Hóa", "Nghệ An", "Hà Tĩnh", "Quảng Bình", "Quảng Trị",
            "Thừa Thiên Huế", "Quảng Nam", "Quảng Ngãi", "Bình Định",
            "Phú Yên", "Khánh Hòa", "Ninh Thuận", "Bình Thuận",
            "Kon Tum", "Gia Lai", "Đắk Lắk", "Đắk Nông", "Lâm Đồng",
            "Bình Phước", "Tây Ninh", "Bình Dương", "Đồng Nai", "Bà Rịa - Vũng Tàu",
            "Long An", "Tiền Giang", "Bến Tre", "Trà Vinh", "Vĩnh Long",
            "Đồng Tháp", "An Giang", "Kiên Giang", "Hậu Giang", "Sóc Trăng",
            "Bạc Liêu", "Cà Mau"
    );

    /**
     * Processes a damage assessment task on a newspaper article instance.
     * <p>
     * This method extracts damage information from the article content by:
     * </p>
     * <ol>
     * <li>Identifying all provinces mentioned in the article</li>
     * <li>Extracting damage statistics for each province</li>
     * <li>Aggregating the results into a damage assessment result</li>
     * </ol>
     *
     * @param task the damage assessment task to execute
     * @param instance the newspaper article instance to analyze
     * @return a damage assessment result containing extracted damage information by date and type
     */
    @Override
    public DamageAssessmentTask.Result process(DamageAssessmentTask task, NewspaperArticleInstance instance) {
        DamageAssessmentTask.Result result = new DamageAssessmentTask.Result();
        String content = instance.getContent();
        LocalDate date = instance.getPostedDate();

        if (content == null || content.isEmpty() || date == null) {
            return result;
        }

        // Extract provinces mentioned in the text
        Set<String> provincesFound = extractProvincesFromText(content);

        // For each province, extract damage information
        for (String province : provincesFound) {
            Map<DamageAssessmentTask.Result.DamageCount.DamageType, Integer> damageInfo = extractDamageInfoByProvince(content, province);

            // Merge damage counts into the result
            for (Map.Entry<DamageAssessmentTask.Result.DamageCount.DamageType, Integer> entry : damageInfo.entrySet()) {
                result.increment(date, entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    /**
     * Extracts the list of Vietnamese provinces mentioned in the article text.
     * <p>
     * This method performs case-insensitive matching to identify province names
     * in the text. It also recognizes common abbreviations such as "TP" (thành phố)
     * for city names.
     * </p>
     *
     * @param text the article text to analyze
     * @return a set of province names found in the text, or an empty set if none found
     */
    private Set<String> extractProvincesFromText(String text) {
        Set<String> foundProvinces = new TreeSet<>();
        String textLower = text.toLowerCase();

        for (String province : VIETNAM_PROVINCES) {
            if (text.contains(province) || textLower.contains(province.toLowerCase())) {
                foundProvinces.add(province);
            }
            // Check for "TP Hanoi" or "TP. Hanoi" patterns
            if (text.contains("TP " + province) || text.contains("TP. " + province)) {
                foundProvinces.add(province);
            }
        }

        return foundProvinces;
    }

    /**
     * Extracts damage information for a specific province using regular expressions.
     * <p>
     * This method searches the article text for damage statistics associated with
     * the specified province. It uses multiple regex patterns to handle various
     * text formats and structures commonly found in Vietnamese news articles.
     * The method extracts data for categories including casualties, property damage,
     * agricultural losses, livestock deaths, and infrastructure damage.
     * </p>
     *
     * @param text the article text to analyze
     * @param province the name of the province to extract damage information for
     * @return a map of damage types to their corresponding counts for this province
     */
    private Map<DamageAssessmentTask.Result.DamageCount.DamageType, Integer> extractDamageInfoByProvince(String text, String province) {
        Map<DamageAssessmentTask.Result.DamageCount.DamageType, Integer> info = new HashMap<>();
        String escapedProvince = Pattern.quote(province);

        // ============ ABOUT PEOPLE ============

        // Pattern 1: "+ Province: X people (Y dead, Z missing)"
        Pattern pattern1 = Pattern.compile(
                "\\+\\s*" + escapedProvince + "\\s*:\\s*(\\d+)\\s*người\\s*\\(([^)]+)\\)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        Matcher match1 = pattern1.matcher(text);

        if (match1.find()) {
            String details = match1.group(2);

            // Extract deaths
            Matcher deathMatch = Pattern.compile("(\\d+)\\s*(?:người\\s*)?chết", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(details);
            if (deathMatch.find()) {
                info.put(DamageAssessmentTask.Result.DamageCount.DamageType.DEATHS, Integer.parseInt(deathMatch.group(1)));
            }

            // Extract missing
            Matcher missingMatch = Pattern.compile("(\\d+)\\s*(?:người\\s*)?mất\\s*tích", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(details);
            if (missingMatch.find()) {
                info.put(DamageAssessmentTask.Result.DamageCount.DamageType.MISSING, Integer.parseInt(missingMatch.group(1)));
            }

            // Extract injured
            Matcher injuredMatch = Pattern.compile("(\\d+)\\s*(?:người\\s*)?bị\\s*thương", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(details);
            if (injuredMatch.find()) {
                info.put(DamageAssessmentTask.Result.DamageCount.DamageType.INJURIES, Integer.parseInt(injuredMatch.group(1)));
            }
        }

        // Pattern 2: "Province: X deaths" (standalone)
        if (!info.containsKey(DamageAssessmentTask.Result.DamageCount.DamageType.DEATHS)) {
            Pattern pattern2 = Pattern.compile(
                    escapedProvince + "\\s*[:\\-]\\s*(\\d+)\\s*(?:người\\s*)?chết",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            );
            Matcher match2 = pattern2.matcher(text);
            if (match2.find()) {
                info.put(DamageAssessmentTask.Result.DamageCount.DamageType.DEATHS, Integer.parseInt(match2.group(1)));
            }
        }

        // Pattern 3: Injured (in separate section)
        if (!info.containsKey(DamageAssessmentTask.Result.DamageCount.DamageType.INJURIES)) {
            Pattern injuredSectionPattern = Pattern.compile("Người bị thương[^:]*:", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher injuredSection = injuredSectionPattern.matcher(text);
            if (injuredSection.find()) {
                String injuredText = text.substring(injuredSection.start(), Math.min(injuredSection.start() + 2000, text.length()));
                Pattern[] injuredPatterns = {
                        Pattern.compile(escapedProvince + "\\s+(\\d+)(?:\\s*(?:người|,))?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                        Pattern.compile(escapedProvince + "\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                };
                for (Pattern p : injuredPatterns) {
                    Matcher m = p.matcher(injuredText);
                    if (m.find()) {
                        info.put(DamageAssessmentTask.Result.DamageCount.DamageType.INJURIES, Integer.parseInt(m.group(1)));
                        break;
                    }
                }
            }
        }

        // Pattern 4: Missing (standalone)
        if (!info.containsKey(DamageAssessmentTask.Result.DamageCount.DamageType.MISSING)) {
            Pattern[] missingPatterns = {
                    Pattern.compile(escapedProvince + "\\s*[:\\-]\\s*(\\d+)\\s*(?:người\\s*)?mất\\s*tích", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                    Pattern.compile("mất\\s*tích[^:]*" + escapedProvince + "\\s*[:\\-]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            };
            for (Pattern p : missingPatterns) {
                Matcher m = p.matcher(text);
                if (m.find()) {
                    info.put(DamageAssessmentTask.Result.DamageCount.DamageType.MISSING, Integer.parseInt(m.group(1)));
                    break;
                }
            }
        }

        // ============ ABOUT HOUSES ============

        // Damaged houses (collapsed, roof blown off, damaged)
        Pattern[] damagedHousePatterns = {
                Pattern.compile(escapedProvince + "\\s+(\\d+[\\d.,]*)\\s*nhà(?:\\s*(?:sập|tốc\\s*mái|hư\\s*hỏng|hư\\s*hại|ở))?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                Pattern.compile(escapedProvince + "\\s*[:\\-]\\s*(\\d+[\\d.,]*)\\s*nhà", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                Pattern.compile("(?:nhà\\s*ở\\s*bị\\s*hư\\s*hỏng)[^:]*" + escapedProvince + "\\s+(\\d+[\\d.,]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
        };
        for (Pattern p : damagedHousePatterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                int value = parseNumber(m.group(1));
                if (value > 0) {
                    info.put(DamageAssessmentTask.Result.DamageCount.DamageType.DAMAGED_HOUSES, value);
                    break;
                }
            }
        }

        // Flooded houses
        Pattern floodedSectionPattern = Pattern.compile("Nhà\\s*bị\\s*ngập|nhà\\s*ngập", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher floodedSection = floodedSectionPattern.matcher(text);
        if (floodedSection.find()) {
            String floodedText = text.substring(floodedSection.start(), Math.min(floodedSection.start() + 2000, text.length()));
            Pattern[] floodedPatterns = {
                    Pattern.compile(escapedProvince + "\\s*[:\\-]\\s*(\\d+[\\d.,]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                    Pattern.compile(escapedProvince + "\\s+(\\d+[\\d.,]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            };
            for (Pattern p : floodedPatterns) {
                Matcher m = p.matcher(floodedText);
                if (m.find()) {
                    int value = parseNumber(m.group(1));
                    if (value > 0) {
                        info.put(DamageAssessmentTask.Result.DamageCount.DamageType.FLOODED_HOUSES, value);
                        break;
                    }
                }
            }
        }

        // ============ ABOUT AGRICULTURE ============

        // Rice affected (ha)
        Pattern riceSectionPattern = Pattern.compile("lúa\\s*bị|diện\\s*tích\\s*lúa", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher riceSection = riceSectionPattern.matcher(text);
        if (riceSection.find()) {
            String riceText = text.substring(riceSection.start(), Math.min(riceSection.start() + 3000, text.length()));
            Pattern[] ricePatterns = {
                    Pattern.compile(escapedProvince + "\\s+(\\d+[\\d.,]*)\\s*ha", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                    Pattern.compile(escapedProvince + "\\s*[:\\-]\\s*(\\d+[\\d.,]*)\\s*ha", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            };
            for (Pattern p : ricePatterns) {
                Matcher m = p.matcher(riceText);
                if (m.find()) {
                    int value = parseNumber(m.group(1));
                    if (value > 0) {
                        info.put(DamageAssessmentTask.Result.DamageCount.DamageType.RICE, value);
                        break;
                    }
                }
            }
        }

        // Crops affected (ha)
        Pattern cropSectionPattern = Pattern.compile("hoa\\s*màu", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher cropSection = cropSectionPattern.matcher(text);
        if (cropSection.find()) {
            String cropText = text.substring(cropSection.start(), Math.min(cropSection.start() + 3000, text.length()));
            Pattern[] cropPatterns = {
                    Pattern.compile(escapedProvince + "\\s+(\\d+[\\d.,]*)\\s*ha", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                    Pattern.compile(escapedProvince + "\\s*[:\\-]\\s*(\\d+[\\d.,]*)\\s*ha", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            };
            for (Pattern p : cropPatterns) {
                Matcher m = p.matcher(cropText);
                if (m.find()) {
                    int value = parseNumber(m.group(1));
                    if (value > 0) {
                        info.put(DamageAssessmentTask.Result.DamageCount.DamageType.CROPS, value);
                        break;
                    }
                }
            }
        }

        // Fruit trees affected (ha)
        Pattern fruitTreeSectionPattern = Pattern.compile("cây\\s*ăn\\s*quả", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher fruitTreeSection = fruitTreeSectionPattern.matcher(text);
        if (fruitTreeSection.find()) {
            String fruitTreeText = text.substring(fruitTreeSection.start(), Math.min(fruitTreeSection.start() + 3000, text.length()));
            Pattern[] fruitTreePatterns = {
                    Pattern.compile(escapedProvince + "\\s+(\\d+[\\d.,]*)\\s*ha", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                    Pattern.compile(escapedProvince + "\\s*[:\\-]\\s*(\\d+[\\d.,]*)\\s*ha", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            };
            for (Pattern p : fruitTreePatterns) {
                Matcher m = p.matcher(fruitTreeText);
                if (m.find()) {
                    int value = parseNumber(m.group(1));
                    if (value > 0) {
                        info.put(DamageAssessmentTask.Result.DamageCount.DamageType.FRUIT_TREES, value);
                        break;
                    }
                }
            }
        }

        // Aquaculture cages
        Pattern aquacultureSectionPattern = Pattern.compile("lồng\\s*bè|thủy\\s*sản", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher aquacultureSection = aquacultureSectionPattern.matcher(text);
        if (aquacultureSection.find()) {
            String aquacultureText = text.substring(aquacultureSection.start(), Math.min(aquacultureSection.start() + 2000, text.length()));
            Pattern[] aquaculturePatterns = {
                    Pattern.compile(escapedProvince + "\\s+(\\d+[\\d.,]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                    Pattern.compile(escapedProvince + "\\s*[:\\-]\\s*(\\d+[\\d.,]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            };
            for (Pattern p : aquaculturePatterns) {
                Matcher m = p.matcher(aquacultureText);
                if (m.find()) {
                    int value = parseNumber(m.group(1));
                    if (value > 0) {
                        info.put(DamageAssessmentTask.Result.DamageCount.DamageType.AQUACULTURE_CAGES, value);
                        break;
                    }
                }
            }
        }

        // Cattle deaths
        Pattern cattleSectionPattern = Pattern.compile("gia\\s*súc", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher cattleSection = cattleSectionPattern.matcher(text);
        if (cattleSection.find()) {
            String cattleText = text.substring(cattleSection.start(), Math.min(cattleSection.start() + 2000, text.length()));
            Pattern[] cattlePatterns = {
                    Pattern.compile(escapedProvince + "\\s+(\\d+[\\d.,]*)\\s*con", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                    Pattern.compile(escapedProvince + "\\s*[:\\-]\\s*(\\d+[\\d.,]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            };
            for (Pattern p : cattlePatterns) {
                Matcher m = p.matcher(cattleText);
                if (m.find()) {
                    int value = parseNumber(m.group(1));
                    if (value > 0) {
                        info.put(DamageAssessmentTask.Result.DamageCount.DamageType.CATTLE_DEATHS, value);
                        break;
                    }
                }
            }
        }

        // Poultry deaths
        Pattern poultrySectionPattern = Pattern.compile("gia\\s*cầm", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher poultrySection = poultrySectionPattern.matcher(text);
        if (poultrySection.find()) {
            String poultryText = text.substring(poultrySection.start(), Math.min(poultrySection.start() + 2000, text.length()));
            Pattern[] poultryPatterns = {
                    Pattern.compile(escapedProvince + "\\s+(\\d+[\\d.,]*)\\s*con", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                    Pattern.compile(escapedProvince + "\\s*[:\\-]\\s*(\\d+[\\d.,]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            };
            for (Pattern p : poultryPatterns) {
                Matcher m = p.matcher(poultryText);
                if (m.find()) {
                    int value = parseNumber(m.group(1));
                    if (value > 0) {
                        info.put(DamageAssessmentTask.Result.DamageCount.DamageType.POULTRY_DEATHS, value);
                        break;
                    }
                }
            }
        }

        // ============ ABOUT INFRASTRUCTURE ============

        // Electric poles damaged
        Pattern[] electricPolePatterns = {
                Pattern.compile(escapedProvince + "\\s+(\\d+[\\d.,]*)\\s*cột\\s*điện", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                Pattern.compile("cột\\s*điện[^:]*" + escapedProvince + "\\s+(\\d+[\\d.,]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
        };
        for (Pattern p : electricPolePatterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                int value = parseNumber(m.group(1));
                if (value > 0) {
                    info.put(DamageAssessmentTask.Result.DamageCount.DamageType.ELECTRIC_POLES, value);
                    break;
                }
            }
        }

        return info;
    }

    /**
     * Parses a number string that may contain dots or commas as thousand separators.
     * <p>
     * This method handles Vietnamese number formatting where dots and commas are
     * commonly used as thousand separators. It removes these separators before
     * parsing the integer value.
     * </p>
     *
     * @param numStr the number string to parse
     * @return the parsed integer value, or 0 if the string cannot be parsed
     */
    private int parseNumber(String numStr) {
        try {
            String cleaned = numStr.replace(".", "").replace(",", "");
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
