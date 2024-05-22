package io.github.axelfrache.springbox.controller;

import io.github.axelfrache.springbox.model.File;
import io.github.axelfrache.springbox.model.Folder;
import io.github.axelfrache.springbox.model.User;
import io.github.axelfrache.springbox.repository.FileRepository;
import io.github.axelfrache.springbox.repository.FolderRepository;
import io.github.axelfrache.springbox.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Controller
public class FileController {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/springbox/files")
    public String listFiles(@RequestParam(required = false) Long folderId, Model model, @AuthenticationPrincipal UserDetails userDetails) {
        Optional<User> optionalUser = userRepository.findByUsername(userDetails.getUsername());
        if (optionalUser.isEmpty()) {
            return "error";
        }

        User user = optionalUser.get();
        Folder currentFolder = null;
        List<File> files;
        List<Folder> folders;

        if (folderId != null) {
            currentFolder = folderRepository.findById(folderId).orElse(null);
            files = fileRepository.findByFolderAndUser(currentFolder, user);
            folders = folderRepository.findByParentFolder(currentFolder);
        } else {
            files = fileRepository.findByUserAndFolderIsNull(user);
            folders = folderRepository.findByUserAndParentFolderIsNull(user);
        }

        model.addAttribute("files", files);
        model.addAttribute("folders", folders);
        model.addAttribute("currentFolder", currentFolder);
        model.addAttribute("username", userDetails.getUsername());
        return "files";
    }

    @PostMapping("/springbox/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file, @RequestParam(required = false) Long folderId, @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        Optional<User> optionalUser = userRepository.findByUsername(userDetails.getUsername());
        if (optionalUser.isEmpty()) {
            return "error";
        }
        User user = optionalUser.get();
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findById(folderId).orElse(null);
        }
        String fileName = file.getOriginalFilename();
        String filePath = "uploads/" + fileName;
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(file.getBytes());
        }
        File dbFile = new File();
        dbFile.setName(fileName);
        dbFile.setPath(filePath);
        dbFile.setUploadDate(new Date());
        dbFile.setUser(user);
        dbFile.setFolder(folder);
        fileRepository.save(dbFile);
        return STR."redirect:/springbox/files?folderId=\{folder != null ? folder.getId() : ""}";
    }

    @PostMapping("/springbox/folder")
    public String createFolder(@RequestParam("name") String name, @RequestParam(required = false) Long parentFolderId, @AuthenticationPrincipal UserDetails userDetails) {
        Optional<User> optionalUser = userRepository.findByUsername(userDetails.getUsername());
        if (optionalUser.isEmpty()) {
            return "error";
        }
        User user = optionalUser.get();
        Folder parentFolder = null;
        if (parentFolderId != null) {
            parentFolder = folderRepository.findById(parentFolderId).orElse(null);
        }
        Folder folder = new Folder();
        folder.setName(name);
        folder.setUser(user);
        folder.setParentFolder(parentFolder);
        folderRepository.save(folder);
        return STR."redirect:/springbox/files?folderId=\{parentFolder != null ? parentFolder.getId() : ""}";
    }

    @PostMapping("/springbox/folder/delete")
    public String deleteFolder(@RequestParam("id") Long id) {
        deleteFolderAndContents(id);
        return "redirect:/springbox/files";
    }

    @PostMapping("/springbox/file/delete")
    public String deleteFile(@RequestParam("id") Long id) {
        fileRepository.deleteById(id);
        return "redirect:/springbox/files";
    }

    private void deleteFolderAndContents(Long folderId) {
        List<Folder> subFolders = folderRepository.findByParentFolder(folderRepository.findById(folderId).orElse(null));
        for (Folder subFolder : subFolders) {
            deleteFolderAndContents(subFolder.getId());
        }
        List<File> files = fileRepository.findByFolderAndUser(folderRepository.findById(folderId).orElse(null), userRepository.findById(folderRepository.findById(folderId).orElse(null).getUser().getId()).orElse(null));
        for (File file : files) {
            fileRepository.deleteById(file.getId());
        }
        folderRepository.deleteById(folderId);
    }
}