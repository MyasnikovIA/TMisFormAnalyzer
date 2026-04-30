package ru.miacomsoft.service;

import ru.miacomsoft.model.FormInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Сервис для разрешения переопределений форм из UserForms
 * Реализует логику:
 * 1. Полное переопределение - если в UserForms есть .frm файл по тому же пути
 * 2. Частичное переопределение - если есть .d каталог с .dfrm файлами
 */
public class UserFormsResolver {

    private final FileScannerService scannerService;
    private final DfmOverrideProcessor dfmProcessor;

    public UserFormsResolver(FileScannerService scannerService) {
        this.scannerService = scannerService;
        this.dfmProcessor = new DfmOverrideProcessor();
    }

    /**
     * Разрешить все переопределения для указанной формы
     */
    public FormInfo resolveOverrides(String formPath) {
        FormInfo formInfo = new FormInfo(formPath);

        Path projectRoot = scannerService.getProjectRoot();
        List<String> regions = scannerService.findAllUserFormsRegions();
        regions.sort(String::compareTo);

        for (String region : regions) {
            Path regionPath = projectRoot.resolve(region);

            // ПРОВЕРКА 1: Полное переопределение (.frm файл)
            Path regionFullOverride = regionPath.resolve(formPath);
            if (Files.exists(regionFullOverride) && formPath.endsWith(".frm")) {
                formInfo.setFullyReplaced(true);
                formInfo.setReplacementPath(regionFullOverride.toString());
                formInfo.addOverride(new FormInfo.OverrideInfo(
                        region,
                        regionFullOverride.toString(),
                        FormInfo.OverrideInfo.OverrideType.FULL_OVERRIDE
                ));
            }

            // ПРОВЕРКА 2: .d каталог
            String formPathWithoutExt = formPath.replace(".frm", "");
            Path dotDPath = regionPath.resolve(formPathWithoutExt + ".d");

            if (Files.exists(dotDPath) && Files.isDirectory(dotDPath)) {
                try (Stream<Path> walk = Files.walk(dotDPath)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".dfrm"))
                            .forEach(dfrmFile -> {
                                // Добавляем ОДИН override на файл, а не на каждую операцию
                                formInfo.addOverride(new FormInfo.OverrideInfo(
                                        region,
                                        dfrmFile.toString(),
                                        FormInfo.OverrideInfo.OverrideType.DOT_D_OVERRIDE,
                                        null,  // baseTarget
                                        null   // position
                                ));
                            });
                } catch (IOException e) {
                    System.err.println("Error scanning .d directory: " + e.getMessage());
                }
            }

            // ПРОВЕРКА 3: .dfrm файл напрямую (не в .d каталоге)
            Path directDfrm = regionPath.resolve(formPath.replace(".frm", ".dfrm"));
            if (Files.exists(directDfrm) && Files.isRegularFile(directDfrm)) {
                formInfo.addOverride(new FormInfo.OverrideInfo(
                        region,
                        directDfrm.toString(),
                        FormInfo.OverrideInfo.OverrideType.PARTIAL_OVERRIDE,
                        null,
                        null
                ));
            }
        }

        return formInfo;
    }

    /**
     * Получить финальное содержимое формы после применения всех переопределений
     * @param formInfo Информация о форме
     * @return Финальное XML содержимое
     */
    public String getFinalFormContent(FormInfo formInfo) {
        // Если форма полностью заменена - читаем файл замены
        if (formInfo.isFullyReplaced() && formInfo.getReplacementPath() != null) {
            Path replacementPath = Path.of(formInfo.getReplacementPath());
            String content = scannerService.readFileContent(replacementPath);
            if (content == null) {
                System.err.println("Cannot read replacement file: " + replacementPath);
                return null;
            }
            return content;
        }

        // Читаем базовую форму
        Path baseFormPath = scannerService.getBaseFormPath(formInfo.getFormPath());
        String baseContent = scannerService.readFileContent(baseFormPath);

        if (baseContent == null) {
            System.err.println("Cannot read base form: " + baseFormPath);
            return null;
        }

        // Применяем все .dfrm переопределения
        String finalContent = baseContent;
        for (FormInfo.OverrideInfo override : formInfo.getOverrides()) {
            if (override.getType() == FormInfo.OverrideInfo.OverrideType.DOT_D_OVERRIDE) {
                // Читаем .dfrm файл
                Path dfrmPath = Path.of(override.getOverridePath());
                String dfrmContent = scannerService.readFileContent(dfrmPath);
                if (dfrmContent != null && !dfrmContent.isEmpty()) {
                    try {
                        String applied = dfmProcessor.applyOverrides(finalContent, dfrmContent);
                        if (applied != null) {
                            finalContent = applied;
                        }
                    } catch (Exception e) {
                        System.err.println("Error applying override " + dfrmPath + ": " + e.getMessage());
                    }
                }
            }
        }

        return finalContent;
    }
}