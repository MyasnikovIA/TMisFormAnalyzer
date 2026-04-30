package ru.miacomsoft.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Сервис для сканирования файловой системы проекта T-MIS
 */
public class FileScannerService {

    private final String projectRoot;
    private final Path rootPath;

    // Кэш для результатов сканирования
    private Set<String> allBaseForms;
    private Map<String, List<Path>> userFormsFrmCache;
    private Map<String, List<Path>> userFormsDfrmCache;
    private Map<String, Map<String, List<Path>>> userFormsDotDCache;  // Исправлен тип

    public FileScannerService(String projectRoot) {
        this.projectRoot = projectRoot;
        this.rootPath = Paths.get(projectRoot);
        this.allBaseForms = null;
        this.userFormsFrmCache = new HashMap<>();
        this.userFormsDfrmCache = new HashMap<>();
        this.userFormsDotDCache = new HashMap<>();
    }

    /**
     * Найти все базовые формы в каталоге Forms/
     */
    public Set<String> findAllBaseForms() {
        if (allBaseForms != null) {
            return allBaseForms;
        }

        allBaseForms = new LinkedHashSet<>();
        Path formsPath = rootPath.resolve("Forms");

        if (!Files.exists(formsPath)) {
            System.err.println("Каталог Forms не найден: " + formsPath);
            return allBaseForms;
        }

        try (Stream<Path> walk = Files.walk(formsPath)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".frm"))
                    .forEach(p -> {
                        String relativePath = getRelativeFormPath(p);
                        allBaseForms.add(relativePath);
                    });
        } catch (IOException e) {
            System.err.println("Ошибка сканирования Forms: " + e.getMessage());
        }

        return allBaseForms;
    }

    /**
     * Найти все каталоги UserForms в корне проекта
     */
    public List<String> findAllUserFormsRegions() {
        List<String> regions = new ArrayList<>();

        try (Stream<Path> list = Files.list(rootPath)) {
            list.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("UserForms"))
                    .forEach(p -> regions.add(p.getFileName().toString()));
        } catch (IOException e) {
            System.err.println("Ошибка сканирования UserForms: " + e.getMessage());
        }

        return regions;
    }

    /**
     * Найти все файлы .frm в указанном каталоге UserForms
     */
    public List<Path> findUserFormsFrmFiles(String regionName) {
        if (userFormsFrmCache.containsKey(regionName)) {
            return userFormsFrmCache.get(regionName);
        }

        List<Path> files = new ArrayList<>();
        Path regionPath = rootPath.resolve(regionName);

        if (!Files.exists(regionPath)) {
            userFormsFrmCache.put(regionName, files);
            return files;
        }

        try (Stream<Path> walk = Files.walk(regionPath)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".frm"))
                    .forEach(files::add);
        } catch (IOException e) {
            System.err.println("Ошибка сканирования " + regionName + ": " + e.getMessage());
        }

        userFormsFrmCache.put(regionName, files);
        return files;
    }

    /**
     * Найти все файлы .dfrm в указанном каталоге UserForms
     */
    public List<Path> findUserFormsDfrmFiles(String regionName) {
        if (userFormsDfrmCache.containsKey(regionName)) {
            return userFormsDfrmCache.get(regionName);
        }

        List<Path> files = new ArrayList<>();
        Path regionPath = rootPath.resolve(regionName);

        if (!Files.exists(regionPath)) {
            userFormsDfrmCache.put(regionName, files);
            return files;
        }

        try (Stream<Path> walk = Files.walk(regionPath)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".dfrm"))
                    .forEach(files::add);
        } catch (IOException e) {
            System.err.println("Ошибка сканирования " + regionName + ": " + e.getMessage());
        }

        userFormsDfrmCache.put(regionName, files);
        return files;
    }

    /**
     * Найти все каталоги .d в указанном регионе UserForms
     * @return Map: имя формы -> список файлов .dfrm в каталоге .d
     */
    public Map<String, List<Path>> findDotDDirectories(String regionName) {
        if (userFormsDotDCache.containsKey(regionName)) {
            return userFormsDotDCache.get(regionName);
        }

        Map<String, List<Path>> result = new HashMap<>();
        Path regionPath = rootPath.resolve(regionName);

        if (!Files.exists(regionPath)) {
            userFormsDotDCache.put(regionName, result);
            return result;
        }

        try (Stream<Path> walk = Files.walk(regionPath)) {
            walk.filter(Files::isDirectory)
                    .filter(p -> p.toString().endsWith(".d"))
                    .forEach(dotDir -> {
                        // Определяем имя базовой формы
                        String dotDirPath = getRelativeUserFormsPath(dotDir, regionName);
                        String baseFormName = dotDirPath.substring(0, dotDirPath.length() - 2); // Убираем .d

                        // Собираем все .dfrm файлы внутри .d каталога
                        List<Path> dfrmFiles = new ArrayList<>();
                        try (Stream<Path> dirWalk = Files.walk(dotDir)) {
                            dirWalk.filter(Files::isRegularFile)
                                    .filter(p -> p.toString().endsWith(".dfrm"))
                                    .forEach(dfrmFiles::add);
                        } catch (IOException e) {
                            System.err.println("Ошибка сканирования .d каталога: " + e.getMessage());
                        }

                        if (!dfrmFiles.isEmpty()) {
                            result.put(baseFormName, dfrmFiles);
                        }
                    });
        } catch (IOException e) {
            System.err.println("Ошибка сканирования .d каталогов: " + e.getMessage());
        }

        userFormsDotDCache.put(regionName, result);
        return result;
    }

    /**
     * Прочитать содержимое файла
     */
    public String readFileContent(Path filePath) {
        try {
            long fileSize = Files.size(filePath);
            if (fileSize > 50 * 1024 * 1024) {
                System.err.println("Файл слишком большой: " + filePath);
                return null;
            }

            byte[] bytes = Files.readAllBytes(filePath);
            return new String(bytes);
        } catch (IOException e) {
            System.err.println("Ошибка чтения файла: " + filePath + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Получить относительный путь формы от каталога Forms/
     */
    private String getRelativeFormPath(Path absolutePath) {
        Path formsPath = rootPath.resolve("Forms");
        try {
            return formsPath.relativize(absolutePath).toString().replace("\\", "/");
        } catch (IllegalArgumentException e) {
            return absolutePath.toString();
        }
    }

    /**
     * Получить относительный путь от корня UserForms региона
     */
    private String getRelativeUserFormsPath(Path absolutePath, String regionName) {
        Path regionPath = rootPath.resolve(regionName);
        try {
            return regionPath.relativize(absolutePath).toString().replace("\\", "/");
        } catch (IllegalArgumentException e) {
            return absolutePath.toString();
        }
    }

    /**
     * Проверить существование базовой формы с поддержкой разных форматов пути
     */
    public boolean baseFormExists(String formPath) {
        // Нормализуем путь - убираем UserForms префикс если он есть
        String normalizedPath = formPath;

        // Если путь начинается с UserForms, извлекаем путь к базовой форме
        if (normalizedPath.startsWith("UserForms")) {
            String withoutUserForms = normalizedPath.replaceFirst("^/?UserForms[^/]*/", "");
            if (withoutUserForms.contains(".d/")) {
                normalizedPath = withoutUserForms.substring(0, withoutUserForms.indexOf(".d/")) + ".frm";
            } else if (withoutUserForms.endsWith(".dfrm")) {
                normalizedPath = withoutUserForms.substring(0, withoutUserForms.length() - 5) + ".frm";
            } else {
                normalizedPath = withoutUserForms;
            }
        }

        // Пробуем разные варианты пути
        String[] possiblePaths = {
                normalizedPath,                                           // ARMMainDoc/arm_director.frm
                "Forms/" + normalizedPath,                                // Forms/ARMMainDoc/arm_director.frm
                normalizedPath.replaceAll("^Forms/", ""),                 // Если пришел с Forms/
                "Forms/" + normalizedPath.replaceAll("\\.dfrm$", ".frm")  // Замена .dfrm на .frm
        };

        for (String path : possiblePaths) {
            Path baseFormFile = rootPath.resolve(path);
            if (Files.exists(baseFormFile)) {
                return true;
            }
        }

        return false;
    }

    // Добавить метод для получения корня проекта
    public Path getProjectRoot() {
        return rootPath;
    }

    /**
     * Получить абсолютный путь к базовой форме
     */
    public Path getBaseFormPath(String formPath) {
        // Пробуем разные варианты пути
        String[] possiblePaths = {
                formPath,
                "Forms/" + formPath,
                formPath.replaceAll("^Forms/", "")
        };

        for (String path : possiblePaths) {
            Path baseFormFile = rootPath.resolve(path);
            if (Files.exists(baseFormFile)) {
                return baseFormFile;
            }
        }

        // Если не найдено, возвращаем путь с Forms/ как наиболее вероятный
        return rootPath.resolve("Forms").resolve(formPath);
    }

}