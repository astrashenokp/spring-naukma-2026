package com.example.lab07;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/teas")
public class TeaWebController {

	private final TeaService teaService;

	public TeaWebController(TeaService teaService) {
		this.teaService = teaService;
	}

	@GetMapping
	public String listTeas(Model model) {
		model.addAttribute("teas", teaService.findAll());
		return "teas/list";
	}

	@GetMapping("/new")
	public String showCreateForm(Model model) {
		model.addAttribute("form", new CreateTeaForm("", ""));
		return "teas/create-form";
	}

	@PostMapping
	public String handleCreateTea(
			@Valid @ModelAttribute("form") CreateTeaForm form,
			BindingResult bindingResult) {

		if (bindingResult.hasErrors()) {
			return "teas/create-form";
		}

		teaService.addTea(form.name(), form.teaType());

		return "redirect:/teas";
	}

}
